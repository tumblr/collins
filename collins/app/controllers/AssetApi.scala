package controllers

import models.{Status => AStatus}
import models._
import util.{Helpers, LshwParser}

import play.api.data._
import play.api.mvc._
import play.api.json._

object AssetApiProtocol {
  implicit object AssetFormat extends Format[Asset] {
    def writes(o: Asset): JsValue = JsObject(Map(
      "id" -> JsNumber(o.getId),
      "tag" -> JsString(o.tag),
      "status" -> JsString(o.getStatus.name),
      "type" -> JsString(o.getType.name)
    ))
    def reads(json: JsValue): Asset = Asset(
      (json \ "tag").as[String],
      AStatus.Enum.withName((json \ "status").as[String]),
      AssetType.Enum.withName((json \ "type").as[String])
    )
  }
}

trait AssetApi {
  this: Api with SecureController =>

  private lazy val lshwConfig = Helpers.subAsMap("lshw")

  // GET /api/asset/:tag
  def findAssetWithMetaValues(tag: String) = SecureAction { implicit req =>
    val responseData = withAssetFromTag(tag) { asset =>
      ResponseData(Ok, AssetMetaValue.findAllByAssetId(asset.getId).map { wrap =>
        "%s_%d".format(wrap.getName, wrap.getGroupId) -> wrap.getValue
      }.toMap)
    }
    formatResponseData(responseData)
  }

  // PUT /api/asset/:tag
  def createAsset(tag: String) = SecureAction { implicit req =>

    def doCreateAsset(tag: String): ResponseData = {
      import IpmiInfo.Enum._
      try {
        Model.withTransaction { implicit con =>
          val asset = Asset.create(Asset(tag, AStatus.Enum.Incomplete, AssetType.Enum.ServerNode))
          val ipmi = IpmiInfo(asset)
          ResponseData(Created, getCreateMessage(asset, ipmi))
        }
      } catch {
        case e => getErrorMessage(e.getMessage)
      }
    }

    val responseData = Asset.isValidTag(tag) match {
      case false => getErrorMessage("Invalid tag specified")
      case true => Asset.findByTag(tag) match {
        case Some(asset) => Model.withConnection { implicit con =>
          ResponseData(Ok, getCreateMessage(asset, IpmiInfo(asset)))
        }
        case None => doCreateAsset(tag)
      }
    }

    formatResponseData(responseData)
  }

  // POST /api/asset/:tag
  def updateAsset(tag: String) = SecureAction { implicit req =>
    val responseData = withAssetFromTag(tag) { asset =>
      Form("lshw" -> requiredText).bindFromRequest.fold(
        noLshw => {
          val msg = noLshw.errors.map { _.message }.mkString(", ")
          getErrorMessage(msg)
        },
        lshw => doUpdate(asset, lshw)
      )
    }
    formatResponseData(responseData)
  }

  private def withAssetFromTag(tag: String)(f: Asset => ResponseData): ResponseData = {
    Asset.isValidTag(tag) match {
      case false => getErrorMessage("Empty tag specified")
      case true => Asset.findByTag(tag) match {
        case Some(asset) => f(asset)
        case None => getErrorMessage("Could not find specified asset", NotFound)
      }
    }
  }

  private def doUpdate(asset: Asset, lshw: String): ResponseData = {
    val parser = new LshwParser(lshw, lshwConfig)
    val parsed = parser.parse()
    parsed match {
      case Left(e) => getErrorMessage(e.getMessage)
      case Right(lshwRep) => try {
        Model.withTransaction { implicit con =>
          LshwHelper.updateAsset(asset, lshwRep) match {
            case true =>
              ResponseData(Ok, Map("SUCCESS" -> "true"))
            case false =>
              getErrorMessage("Error saving values", InternalServerError)
          }
        }
      } catch {
        case e: Throwable =>
          val msg = "Error saving values: %s".format(e.getMessage)
          getErrorMessage(msg, InternalServerError)
      }
    }
  }

  private def getErrorMessage(msg: String, status: Status = BadRequest) = {
    ResponseData(status, Map("ERROR_DETAILS" -> msg))
  }

  private def getCreateMessage(asset: Asset, ipmi: IpmiInfo): Map[String,String] = {
    ipmi.toMap() ++ Map(
      "ASSET_STATUS" -> asset.getStatus().name,
      "ASSET_TAG" -> asset.tag
    )
  }
}
