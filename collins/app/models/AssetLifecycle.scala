package models

import util.{AssetStateMachine, Helpers, LldpRepresentation, LshwRepresentation}
import util.parsers.{LldpParser, LshwParser}
import play.api.Logger

import java.sql.Connection

// Supports meta operations on assets
object AssetLifecycle {

  private[this] val logger = Logger.logger

  type AssetIpmi = Tuple2[Asset,Option[IpmiInfo]]
  def createAsset(tag: String, assetType: AssetType, generateIpmi: Boolean, status: Option[Status.Enum] = None): Either[Throwable,AssetIpmi] = {
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
        logger.warn("Caught exception creating asset: %s".format(e.getMessage), e)
        Left(e)
    }
  }

  private lazy val lshwConfig = Helpers.subAsMap("lshw")
  def updateAsset(asset: Asset, options: Map[String,String]): Either[Throwable,Boolean] = {
    asset.asset_type == AssetType.Enum.ServerNode.id match {
      case true => updateServer(asset, options)
      case false => updateOther(asset, options)
    }
  }

  protected def updateOther(asset: Asset, options: Map[String,String]): Either[Throwable,Boolean] = {
    try {
      Model.withTransaction { implicit con =>
        AssetStateMachine(asset).update().executeUpdate()
        AssetLog.create(AssetLog.informational(
          asset,
          "Asset state updated",
          AssetLog.Formats.PlainText,
          AssetLog.Sources.Internal
        ))
        Right(true)
      }
    } catch {
      case e: Throwable =>
        handleException(asset, "Error saving values or in state transition", e)
    }
  }

  protected def updateServer(asset: Asset, options: Map[String,String]): Either[Throwable,Boolean] = {
    if (asset.status == Status.Enum.Incomplete.id) {
      updateIncompleteServer(asset, options)
    } else {
      Left(new Exception("Only updates for Incomplete servers are currently supported"))
    }
  }

  protected def updateIncompleteServer(asset: Asset, options: Map[String,String]): Either[Throwable,Boolean] = {
    val lshw = options.get("lshw")
    val lldp = options.get("lldp")
    val chassis_tag = options.get("chassis_tag")

    if (!lshw.isDefined) {
      return Left(new Exception("lshw data not specified"))
    } else if (!chassis_tag.isDefined) {
      return Left(new Exception("chassis_tag data not specified"))
    } else if (!lldp.isDefined) {
      return Left(new Exception("lldp data not specified"))
    }
    val excluded = Set("lshw", "lldp", "chassis_tag")
    val restricted = AssetMeta.Enum.values.map { _.toString }.toSet
    val filteredOptions = options.filter { case(k,v) =>
      !excluded.contains(k)
    }
    filteredOptions.foreach { case(k,v) =>
      if (restricted.contains(k)) {
        return Left(new Exception("Attribute %s is restricted".format(k)))
      }
    }
    val lshwParser = new LshwParser(lshw.get, lshwConfig)
    val lldpParser = new LldpParser(lldp.get)
    try {
      Model.withTransaction { implicit con =>
        val lshwParsingResults = parseLshw(asset, lshwParser)
        if (lshwParsingResults.isLeft) {
          throw lshwParsingResults.left.get
        }
        val lldpParsingResults = parseLldp(asset, lldpParser)
        if (lldpParsingResults.isLeft) {
          throw lldpParsingResults.left.get
        }
        AssetMetaValue.create(AssetMetaValue(asset, AssetMeta.Enum.ChassisTag.id, chassis_tag.get))
        MetaWrapper.createMeta(asset, filteredOptions)
        AssetStateMachine(asset).update().executeUpdate()
        AssetLog.informational(asset, "Parsing and storing LSHW data succeeded, asset now New",
          AssetLog.Formats.PlainText, AssetLog.Sources.Internal
        ).create()
        Right(true)
      }
    } catch {
      case e: Throwable =>
        handleException(asset, "Exception updating asset", e)
        Left(e)
    }
  }

  protected def parseLshw(asset: Asset, parser: LshwParser)(implicit con: Connection): Either[Throwable,LshwRepresentation] = {
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

  protected def parseLldp(asset: Asset, parser: LldpParser)(implicit con: Connection): Either[Throwable,LldpRepresentation] = {
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

  private def handleException(asset: Asset, msg: String, e: Throwable): Either[Throwable,Boolean] = {
    logger.warn(msg, e)
    try {
      Model.withConnection { implicit con =>
        AssetLog.create(AssetLog.error(
          asset,
          msg,
          AssetLog.Formats.PlainText,
          AssetLog.Sources.Internal
        ).withException(e))
      }
    } catch {
      case ex =>
        logger.error("Database problems", ex)
    }
    Left(e)
  }
}
