package controllers

import play.api._
import play.api.data._
import play.api.mvc._
import models.{Asset, AssetMeta, AssetType, IpmiInfo, Model, Status => AStatus}
import util._
import java.io.File

trait Api extends Controller {
  this: SecureController =>

  case class ResponseData(status: Status, data: Map[String,String])

  implicit val securitySpec = SecuritySpec(isSecure = true, requiredCredentials = Seq("infra"))
  val defaultOutputType = JsonOutput()

  def asset(tag: String) = SecureAction { implicit req =>
    val responseData = tag.isEmpty match {
      case true => ResponseData(BadRequest, Map("Details" -> "Empty tag specified"))
      case false => Asset.findBySecondaryId(tag) match {
        case Some(asset) => updateAsset(asset)
        case None => createAsset(tag)
      }
    }
    formatResponseData(tag, responseData)
  }

  private def updateAsset(asset: Asset)(implicit req: Request[AnyContent]): ResponseData = {
    req.body match {
      case b if b.asMultipartFormData.isDefined =>
        val parts = b.asMultipartFormData.get.asUrlFormEncoded
        val lshw = parts.get("lshw").map { data =>
        }
        ResponseData(NotImplemented, Map("Details" -> "In progress"))
      case n =>
        ResponseData(BadRequest, Map("Details" -> "Expected file uploads"))
    }
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
        ResponseData(BadRequest, Map("Details" -> e.getMessage))
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
        Ok("json - " + tag).as(o.contentType)
    }
  }

  def getOutputType(request: Request[AnyContent]): OutputType = OutputType(request) match {
    case Some(ot) => ot
    case None => defaultOutputType
  }
}
