package controllers

import play.api._
import play.api.data._
import play.api.json._
import play.api.mvc._
import models.{Status => AStatus}
import models._
import util._
import java.io.File

trait Api extends Controller {
  this: SecureController =>

  case class ResponseData(status: Status, data: Map[String,String])

  lazy val lshwConfig = Helpers.subAsMap("lshw")
  implicit val securitySpec = SecuritySpec(isSecure = true, requiredCredentials = Seq("infra"))
  val defaultOutputType = JsonOutput()

  def findAssetMetaValues(tag: String) = SecureAction { implicit req =>
    val responseData = tag.isEmpty match {
      case true => ResponseData(BadRequest, Map("ERROR_DETAILS" -> "Empty tag specified"))
      case false => Asset.findBySecondaryId(tag) match {
        case Some(asset) =>
          ResponseData(Ok, AssetMetaValue.findAllByAssetId(asset.getId).map { wrap =>
            "%s_%d".format(wrap.getName, wrap.getGroupId) -> wrap.getValue
          }.toMap)
        case None =>
          ResponseData(NotFound, Map("ERROR_DETAILS" -> "Could not find specified asset"))
      }
    }
    formatResponseData(tag, responseData)
  }

  // FIXME refuse update if asset isn't in right state
  def asset(tag: String) = SecureAction { implicit req =>
    val responseData = tag.isEmpty match {
      case true => ResponseData(BadRequest, Map("ERROR_DETAILS" -> "Empty tag specified"))
      case false => Asset.findBySecondaryId(tag) match {
        case Some(asset) => updateAsset(asset)
        case None => createAsset(tag)
      }
    }
    formatResponseData(tag, responseData)
  }

  private def handleUpdate(asset: Asset, lshw: String): ResponseData = {
    val parser = new LshwParser(lshw, lshwConfig)
    val parsed = parser.parse()
    parsed match {
      case Left(e) => ResponseData(BadRequest, Map("ERROR_DETAILS" -> e.getMessage))
      case Right(lshwRep) => try {
        Model.withTransaction { implicit con =>
          LshwHelper.updateAsset(asset, lshwRep) match {
            case true =>
              ResponseData(Ok, Map("SUCCESS" -> "Yes"))
            case false =>
              ResponseData(InternalServerError, Map("ERROR_DETAILS" -> "Error saving values"))
          }
        }
      } catch {
        case e: Throwable =>
          ResponseData(
            InternalServerError,
            Map("ERROR_DETAILS" -> ("Error saving values: " + e.getMessage)))
      }
    }
  }

  private def updateAsset(asset: Asset)(implicit req: Request[AnyContent]): ResponseData = {
    Form("lshw" -> requiredText).bindFromRequest.fold(
      noLshw => ResponseData(
        BadRequest,
        Map(
          "ERROR_DETAILS" -> noLshw.errors.map { err =>
            err.message
          }.mkString(", ")
        )
      ),
      lshw => handleUpdate(asset, lshw)
    )
  }

  private def createAsset(tag: String)(implicit req: Request[AnyContent]): ResponseData = {
    import IpmiInfo.Enum._
    try {
      Model.withTransaction { implicit con =>
        val asset = Asset.create(Asset(tag, AStatus.Enum.Incomplete, AssetType.Enum.ServerNode))
        val ipmi = IpmiInfo(asset)
        ResponseData(Created, Map(
          IpmiAddress.toString -> ipmi.dottedAddress,
          IpmiGateway.toString -> ipmi.dottedGateway,
          IpmiNetmask.toString -> ipmi.dottedNetmask,
          IpmiUsername.toString -> ipmi.username,
          IpmiPassword.toString -> ipmi.decryptedPassword,
          "ASSET_STATUS" -> "Incomplete",
          "ASSET_TAG" -> tag
        ))
      }
    } catch {
      case e =>
        ResponseData(BadRequest, Map("ERROR_DETAILS" -> e.getMessage))
    }
  }

  private def formatResponseData(tag: String, response: ResponseData)(implicit req: Request[AnyContent]) = {
    getOutputType(req) match {
      case o: TextOutput =>
        response.status(response.data.mkString("\n")).as(o.contentType)
      case o: BashOutput =>
        response.status(response.data.map { case(k, v) =>
          "%s=%s".format(k,v)
        }.mkString("\n") + "\n").as(o.contentType)
      case o =>
        val jsonMap = JsObject(response.data.map { case(k, v) =>
          (k -> JsString(v))
        })
        response.status(stringify(jsonMap)).as(o.contentType)
    }
  }

  def getOutputType(request: Request[AnyContent]): OutputType = OutputType(request) match {
    case Some(ot) => ot
    case None => defaultOutputType
  }
}
