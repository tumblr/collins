package controllers

import models.{Status => AStatus}
import models._
import util.{AssetStateMachine, Helpers, LshwParser}

import play.api.data._
import play.api.mvc._
import play.api.json._

trait AssetApi {
  this: Api with SecureController =>

  private lazy val lshwConfig = Helpers.subAsMap("lshw")

  // GET /api/asset/:tag
  def findAssetWithMetaValues(tag: String) = SecureAction { implicit req =>
    val responseData = withAssetFromTag(tag) { asset =>
      val assetAsMap = assetToJsMap(asset)
      val attribsObj = JsObject(AssetMetaValue.findAllByAssetId(asset.getId).map { wrap =>
        "%s_%d".format(wrap.getName, wrap.getGroupId) -> JsString(wrap.getValue)
      }.toMap)
      val attribMap = Map("ATTRIBS" -> attribsObj)
      ResponseData(Ok, JsObject(assetAsMap ++ attribMap))
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
              AssetStateMachine(asset).update().executeUpdate()
              ResponseData(Ok, JsObject(Map("SUCCESS" -> JsBoolean(true))))
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
    ResponseData(status, JsObject(Map("ERROR_DETAILS" -> JsString(msg))))
  }

  private def getCreateMessage(asset: Asset, ipmi: IpmiInfo): JsObject = {
    val assetAsMap = assetToJsMap(asset)
    val ipmiAsMap = ipmi.toMap().map { case(k,v) =>
      k -> JsString(v)
    }
    JsObject(assetAsMap ++ Map("IPMI" -> JsObject(ipmiAsMap)))
  }

  private def assetToJsMap(asset: Asset) = Map("ASSET" -> JsObject(asset.toMap().map { case(k,v) =>
    k -> JsString(v)
  }))

}
