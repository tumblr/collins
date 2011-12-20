package models

import util.{AssetStateMachine, Helpers, LshwParser}
import play.api.Logger

// Supports meta operations on assets
object AssetLifecycle {

  private[this] val logger = Logger.logger

  type AssetIpmi = Tuple2[Asset,Option[IpmiInfo]]
  def createAsset(tag: String, assetType: AssetType, generateIpmi: Boolean): Either[Throwable,AssetIpmi] = {
    import IpmiInfo.Enum._
    try {
      Model.withTransaction { implicit con =>
        val asset = Asset.create(Asset(tag, Status.Enum.Incomplete, assetType))
        val ipmi = generateIpmi match {
          case true => Some(IpmiInfo.createForAsset(asset))
          case false => None
        }
        AssetLog.create(AssetLog.informational(
          asset,
          "Initial intake successful, status now Incomplete",
          AssetLog.Formats.PlainText,
          AssetLog.Sources.Internal
        ))
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
    val lshw = options.get("lshw")
    val lldpd = options.get("lldpd")
    val chassis_tag = options.get("chassis_tag")

    if (!lshw.isDefined) {
      return Left(new Exception("lshw data not specified"))
    } else if (!chassis_tag.isDefined) {
      return Left(new Exception("chassis_tag data not specified"))
    }
    val parser = new LshwParser(lshw.get, lshwConfig)
    parser.parse() match {
      case Left(ex) => {
        Model.withConnection { implicit con =>
          AssetLog.create(AssetLog.notice(
            asset,
            "Parsing LSHW failed",
            AssetLog.Formats.PlainText,
            AssetLog.Sources.Internal
          ).withException(ex))
        }
        Left(ex)
      }
      case Right(lshwRep) => try {
        Model.withTransaction { implicit con =>
          AssetMetaValue.create(AssetMetaValue(asset, AssetMeta.Enum.ChassisTag.id, chassis_tag.get))
          LshwHelper.updateAsset(asset, lshwRep) match {
            case true =>
              AssetStateMachine(asset).update().executeUpdate()
              AssetLog.create(AssetLog.informational(
                asset,
                "Parsing and storing LSHW data succeeded, asset now New",
                AssetLog.Formats.PlainText,
                AssetLog.Sources.Internal
              ))
              Right(true)
            case false =>
              val ex = new Exception("Parsing LSHW succeeded, saving failed")
              AssetLog.create(AssetLog.error(
                asset,
                "Parsing LSHW succeeded but saving it failed",
                AssetLog.Formats.PlainText,
                AssetLog.Sources.Internal
              ).withException(ex))
              Left(ex)
          }
        }
      } catch {
        case e: Throwable =>
          handleException(asset, "Error saving values or in state transition", e)
      } //catch
    } // parser.parse
  } // updateServer

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
