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
        AssetLog.create(AssetLog(
          asset,
          "Initial intake successful, status now Incomplete",
          false
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
  def updateAsset(asset: Asset, lshw: Option[String], lldpd: Option[String]): Either[Throwable,Boolean] = {
    asset.asset_type == AssetType.Enum.ServerNode.id match {
      case true => updateServer(asset, lshw, lldpd)
      case false => updateOther(asset, lshw, lldpd)
    }
  }

  protected def updateOther(asset: Asset, lshw: Option[String], lldpd: Option[String]): Either[Throwable,Boolean] = {
     try {
        Model.withTransaction { implicit con =>
          AssetStateMachine(asset).update().executeUpdate()
          AssetLog.create(AssetLog(asset, "Asset updated", false))
          Right(true)
        }
      } catch {
        case e: Throwable =>
          logger.warn("Error saving values or in state transition", e)
          try {
            Model.withConnection { implicit con =>
              AssetLog.create(AssetLog(asset, "Error saving values", e))
            }
          } catch {
            case ex =>
              logger.error("Database problem", ex)
          }
          Left(e)
      }
  }

  protected def updateServer(asset: Asset, lshw: Option[String], lldpd: Option[String]): Either[Throwable,Boolean] = {
    if (!lshw.isDefined) {
      return Left(new Exception("LSHW data not specified"))
    }
    val parser = new LshwParser(lshw.get, lshwConfig)
    parser.parse() match {
      case Left(ex) => {
        Model.withConnection { implicit con =>
          AssetLog.create(AssetLog(asset, "Parsing LSHW failed", ex))
        }
        Left(ex)
      }
      case Right(lshwRep) => try {
        Model.withTransaction { implicit con =>
          LshwHelper.updateAsset(asset, lshwRep) match {
            case true =>
              AssetStateMachine(asset).update().executeUpdate()
              AssetLog.create(AssetLog(asset, "Parsing and storing LSHW data succeeded, asset now New", false))
              Right(true)
            case false =>
              val ex = new Exception("Parsing LSHW succeeded, saving failed")
              AssetLog.create(AssetLog(asset, ex))
              Left(ex)
          }
        }
      } catch {
        case e: Throwable =>
          logger.warn("Error saving values", e)
          try {
            Model.withConnection { implicit con =>
              AssetLog.create(AssetLog(asset, "Error saving values", e))
            }
          } catch {
            case ex =>
              logger.error("Database problem", ex)
          }
          Left(e)
      } //catch
    } // parser.parse
  } // updateServer

}
