package models

import conversions._
import AssetMeta.Enum.{PowerPort, RackPosition}
import models.{Status => AStatus}

import util.{ApiTattler, AssetStateMachine, Helpers, InternalTattler, LldpRepresentation, LshwRepresentation}
import util.parsers.{LldpParser, LshwParser}
import Helpers.formatPowerPort

import play.api.Logger

import scala.util.control.Exception.allCatch
import java.sql.Connection
import java.util.Date

// Supports meta operations on assets
object AssetLifecycle {
  // Don't want people trying to set status/tag/etc via attribute
  val POSSIBLE_ASSET_KEYS = Set("STATUS", "TAG", "TYPE")
  val EXCLUDED_KEYS = Set(AssetMeta.Enum.ChassisTag.toString)
  val RESTRICTED_KEYS = AssetMeta.Enum.values.map { _.toString }.toSet ++ POSSIBLE_ASSET_KEYS -- EXCLUDED_KEYS

  private[this] val logger = Logger.logger

  type AssetIpmi = Tuple2[Asset,Option[IpmiInfo]]
  type Status[T] = Either[Throwable,T]

  def createAsset(tag: String, assetType: AssetType, generateIpmi: Boolean, status: Option[Status.Enum] = None): Status[AssetIpmi] = {
    import IpmiInfo.Enum._
    try {
      val _status = status.getOrElse(Status.Enum.Incomplete)
      Model.withTransaction { implicit con =>
        val asset = Asset.create(Asset(tag, _status, assetType))
        val ipmi = generateIpmi match {
          case true => Some(IpmiInfo.createForAsset(asset))
          case false => None
        }
        InternalTattler.informational(asset, None,
          "Initial intake successful, status now %s".format(_status.toString), con)
        Right(Tuple2(asset, ipmi))
      }
    } catch {
      case e =>
        // FIXME once we have logging for non-assets
        logger.warn("Caught exception creating asset: %s".format(e.getMessage), e)
        Left(e)
    }
  }

  def decommissionAsset(asset: Asset, options: Map[String,String]): Status[Boolean] = {
    val reason = options.get("reason").map { r =>
      r + " : status is %s".format(asset.getStatus().name)
    }.getOrElse(
      "Decommission of asset requested, status is %s".format(asset.getStatus().name)
    )
    try {
      Model.withTransaction { implicit con =>
        InternalTattler.informational(asset, None, reason, con)
        AssetStateMachine(asset).decommission()
        InternalTattler.informational(asset, None, "Asset decommissioned successfully", con)
      }
      Right(true)
    } catch {
      case e => Left(e)
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
        val nextState = Status.Enum(asset.status) match {
          case Status.Enum.Incomplete => Status.Enum.New
          case Status.Enum.New => Status.Enum.Unallocated
          case Status.Enum.Unallocated => Status.Enum.Allocated
          case Status.Enum.Allocated => Status.Enum.Cancelled
          case Status.Enum.Cancelled => Status.Enum.Decommissioned
          case Status.Enum.Maintenance => Status.Enum.Unallocated
          case n => n
        }
        val newAsset = asset.copy(status = nextState.id)
        Asset.update(newAsset)
        InternalTattler.informational(newAsset, None, "Asset state updated")
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

  def updateAssetAttributes(asset: Asset, options: Map[String,String]): Status[Boolean] = {
    allCatch[Boolean].either {
      options.find(kv => RESTRICTED_KEYS(kv._1)).map(kv =>
        return Left(new Exception("Attribute %s is restricted".format(kv._1)))
      )
      Model.withTransaction { implicit con =>
        MetaWrapper.createMeta(asset, options)
        true
      }
    }.left.map(e => handleException(asset, "Error saving attributes for asset", e))
  }

  def updateAssetStatus(asset: Asset, options: Map[String,String], con: Connection): Status[Boolean] = {
    Helpers.haveFeature("sloppyStatus") match {
      case Some(true) =>
      case _ =>
        return Left(new Exception("sloppyStatus not enabled"))
    }
    implicit val conn: Connection = con
    val stat = options.get("status").getOrElse("none")
    allCatch[Boolean].either {
      val status = AStatus.Enum.withName(stat)
      if (status.id == asset.status) {
        return Right(true)
      }
      val old = AStatus.Enum(asset.status).toString
      val defaultReason = "Asset state updated from %s to %s".format(old, stat)
      val reason = options.get("reason").map(r => defaultReason + ": " + r).getOrElse(defaultReason)
      Asset.update(asset.copy(status = status.id, updated = Some(new Date().asTimestamp)))
      ApiTattler.warning(asset, None, reason, con)
      true
    }.left.map(e => handleException(asset, "Error updating status for asset", e))
  }

  def updateAssetStatus(asset: Asset, options: Map[String,String]): Status[Boolean] = {
    Model.withTransaction { con =>
      updateAssetStatus(asset, options, con)
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

    val res = allCatch[Boolean].either {
      val values = Seq(
        AssetMetaValue(asset, RackPosition, rackpos),
        AssetMetaValue(asset, PowerPort, 0, power1),
        AssetMetaValue(asset, PowerPort, 1, power2))
      Model.withTransaction { implicit con =>
        val created = AssetMetaValue.create(values)
        require(created == values.length,
          "Should have created %d rows, created %d".format(values.length, created))
        val newAsset = asset.copy(status = Status.Enum.Unallocated.id)
        Asset.update(newAsset)
        MetaWrapper.createMeta(newAsset, filtered)
        true
      }
    }
    res.left.map(e => handleException(asset, "Exception updating asset", e))
  }

  protected def updateIncompleteServer(asset: Asset, options: Map[String,String]): Status[Boolean] = {
    val requiredKeys = Set("lshw", "lldp", "CHASSIS_TAG")
    requiredKeys.find(key => !options.contains(key)).map { not_found =>
      return Left(new Exception(not_found + " parameter not specified"))
    }

    val lshw = options("lshw")
    val lldp = options("lldp")
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
        val newAsset = asset.copy(status = Status.Enum.New.id)
        Asset.update(newAsset)
        InternalTattler.informational(newAsset, None,
          "Parsing and storing LSHW data succeeded",
          con
        )
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
