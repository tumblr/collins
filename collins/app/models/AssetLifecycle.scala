package models

import AssetMeta.Enum.{PowerPort, RackPosition}
import util.{AssetStateMachine, Helpers, LldpRepresentation, LshwRepresentation}
import util.parsers.{LldpParser, LshwParser}
import Helpers.formatPowerPort

import play.api.Logger

import scala.util.control.Exception.allCatch
import java.sql.Connection

// Supports meta operations on assets
object AssetLifecycle {
  val RESTRICTED_KEYS = AssetMeta.Enum.values.map { _.toString }.toSet

  private[this] val logger = Logger.logger

  type AssetIpmi = Tuple2[Asset,Option[IpmiInfo]]
  type Status[T] = Either[Throwable,T]

  def createAsset(tag: String, assetType: AssetType, generateIpmi: Boolean, status: Option[Status.Enum] = None): Status[AssetIpmi] = {
    import IpmiInfo.Enum._
    try {
      Model.withTransaction { implicit con =>
        val _status = status.getOrElse(Status.Enum.Incomplete)
        val asset = Asset.create(Asset(tag, _status, assetType))
        val ipmi = generateIpmi match {
          case true => Some(IpmiInfo.createForAsset(asset))
          case false => None
        }
        AssetLog.informational(
          asset,
          "Initial intake successful, status now %s".format(_status.toString),
          AssetLog.Formats.PlainText,
          AssetLog.Sources.Internal
        ).create()
        Right(Tuple2(asset, ipmi))
      }
    } catch {
      case e =>
        // FIXME once we have logging for non-assets
        logger.warn("Caught exception creating asset: %s".format(e.getMessage), e)
        Left(e)
    }
  }

  private lazy val lshwConfig = Helpers.subAsMap("lshw")
  def updateAsset(asset: Asset, options: Map[String,String]): Status[Boolean] = {
    asset.asset_type == AssetType.Enum.ServerNode.id match {
      case true => updateServer(asset, options)
      case false => updateOther(asset, options)
    }
  }

  protected def updateOther(asset: Asset, options: Map[String,String]): Status[Boolean] = {
    allCatch[Boolean].either {
      Model.withTransaction { implicit con =>
        AssetStateMachine(asset).update().executeUpdate()
        AssetLog.informational(
          asset,
          "Asset state updated",
          AssetLog.Formats.PlainText,
          AssetLog.Sources.Internal
        ).create()
        true
      }
    }.left.map(e => handleException(asset, "Error saving values or in state transition", e))
  }

  protected def updateServer(asset: Asset, options: Map[String,String]): Status[Boolean] = {
    Status.Enum(asset.status) match {
      case Status.Enum.Incomplete =>
        updateIncompleteServer(asset, options)
      case Status.Enum.New =>
        updateNewServer(asset, options)
      case _ =>
        Left(new Exception("Only updates for Incomplete and New servers are currently supported"))
    }
  }

  protected def updateNewServer(asset: Asset, options: Map[String,String]): Status[Boolean] = {
    val requiredKeys = Set(RackPosition.toString, formatPowerPort("A"), formatPowerPort("B"))
    requiredKeys.find(key => !options.contains(key)).map { not_found =>
      return Left(new Exception(not_found + " parameter not specified"))
    }

    val rackpos = options(RackPosition.toString)
    val power1 = options(formatPowerPort("A"))
    val power2 = options(formatPowerPort("B"))

    val filtered = options.filter(kv => !requiredKeys(kv._1))
    filtered.find(kv => RESTRICTED_KEYS(kv._1)).map(kv =>
      return Left(new Exception("Attribute %s is restricted".format(kv._1)))
    )

    allCatch[Boolean].either {
      val values = Seq(
        AssetMetaValue(asset, RackPosition, rackpos),
        AssetMetaValue(asset, PowerPort, 0, power1),
        AssetMetaValue(asset, PowerPort, 1, power2))
      Model.withTransaction { implicit con =>
        val created = AssetMetaValue.create(values)
        require(created == values.length,
          "Should have created %d rows, created %d".format(values.length, created))
        AssetStateMachine(asset).update().executeUpdate()
        true
      }
    }.left.map(e => handleException(asset, "Exception updating asset", e))
  }

  protected def updateIncompleteServer(asset: Asset, options: Map[String,String]): Status[Boolean] = {
    val requiredKeys = Set("LSHW", "LLDP", "CHASSIS_TAG")
    requiredKeys.find(key => !options.contains(key)).map { not_found =>
      return Left(new Exception(not_found + " parameter not specified"))
    }

    val lshw = options("LSHW")
    val lldp = options("LLDP")
    val chassis_tag = options("CHASSIS_TAG")

    val filtered = options.filter(kv => !requiredKeys(kv._1))
    filtered.find(kv => RESTRICTED_KEYS(kv._1)).map(kv =>
      return Left(new Exception("Attribute %s is restricted".format(kv._1)))
    )
    val lshwParser = new LshwParser(lshw, lshwConfig)
    val lldpParser = new LldpParser(lldp)

    allCatch[Boolean].either {
      Model.withTransaction { implicit con =>
        val lshwParsingResults = parseLshw(asset, lshwParser)
        if (lshwParsingResults.isLeft) {
          throw lshwParsingResults.left.get
        }
        val lldpParsingResults = parseLldp(asset, lldpParser)
        if (lldpParsingResults.isLeft) {
          throw lldpParsingResults.left.get
        }
        AssetMetaValue.create(AssetMetaValue(asset, AssetMeta.Enum.ChassisTag.id, chassis_tag))
        MetaWrapper.createMeta(asset, filtered)
        AssetStateMachine(asset).update().executeUpdate()
        AssetLog.informational(asset, "Parsing and storing LSHW data succeeded, asset now New",
          AssetLog.Formats.PlainText, AssetLog.Sources.Internal
        ).create()
        true
      }
    }.left.map(e => handleException(asset, "Exception updating asset", e))
  }

  protected def parseLshw(asset: Asset, parser: LshwParser)(implicit con: Connection): Status[LshwRepresentation] = {
    parser.parse() match {
      case Left(ex) =>
        AssetLog.notice(asset, "Parsing LSHW failed", AssetLog.Formats.PlainText,
          AssetLog.Sources.Internal).withException(ex).create()
        Left(ex)
      case Right(lshwRep) =>
        LshwHelper.updateAsset(asset, lshwRep) match {
          case true =>
            Right(lshwRep)
          case false =>
            val ex = new Exception("Parsing LSHW succeeded, saving failed")
            AssetLog.error(asset, "Parsing LSHW succeeded but saving it failed",
              AssetLog.Formats.PlainText, AssetLog.Sources.Internal
            ).withException(ex).create()
            Left(ex)
        }
    } //catch
  } // updateServer

  protected def parseLldp(asset: Asset, parser: LldpParser)(implicit con: Connection): Status[LldpRepresentation] = {
    parser.parse() match {
      case Left(ex) =>
        AssetLog.notice(asset, "Parsing LLDP failed", AssetLog.Formats.PlainText,
          AssetLog.Sources.Internal).withException(ex).create()
        Left(ex)
      case Right(lldpRep) =>
        LldpHelper.updateAsset(asset, lldpRep) match {
          case true =>
            Right(lldpRep)
          case false =>
            val ex = new Exception("Parsing LLDP succeeded, saving failed")
            AssetLog.error(asset, "Parsing LLDP succeeded but saving it failed",
              AssetLog.Formats.PlainText, AssetLog.Sources.Internal
            ).withException(ex).create()
            Left(ex)
        }
    }
  }

  private def handleException(asset: Asset, msg: String, e: Throwable): Throwable = {
    logger.warn(msg, e)
    try {
      Model.withConnection { implicit con =>
        AssetLog.error(
          asset,
          msg,
          AssetLog.Formats.PlainText,
          AssetLog.Sources.Internal
        ).withException(e).create()
      }
    } catch {
      case ex =>
        logger.error("Database problems", ex)
    }
    e
  }
}
