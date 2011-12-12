package controllers

import play.api._
import play.api.mvc._
import models.{Asset, AssetMeta, AssetType, IpmiInfo, Model, Status => AStatus}
import util._

trait Api extends Controller {
  this: SecureController =>

  implicit val securitySpec = SecuritySpec(isSecure = true, requiredCredentials = Seq("infra"))
  val defaultOutputType = JsonOutput()

  def asset(tag: String) = SecureAction { implicit req =>
    val responseMap = Asset.findBySecondaryId(tag) match {
      case Some(asset) => updateAsset(asset)
      case None => createAsset(tag)
    }
    formatMap(tag, responseMap)
  }

  private def updateAsset(asset: Asset)(implicit req: Request[AnyContent]): Map[String,String] = {
    Map.empty
  }
  private def createAsset(tag: String)(implicit req: Request[AnyContent]): Map[String,String] = {
    import IpmiInfo.Enum._
    Model.withTransaction { implicit con =>
      val asset = Asset.create(Asset(tag, AStatus.Enum.Incomplete, AssetType.Enum.ServerNode))
      val ipmi = IpmiInfo(asset)
      Map(
        IpmiAddress.toString -> ipmi.dottedAddress,
        IpmiGateway.toString -> ipmi.dottedGateway,
        IpmiNetmask.toString -> ipmi.dottedNetmask,
        IpmiUsername.toString -> ipmi.username,
        IpmiPassword.toString -> ipmi.decryptedPassword
      )
    }
  }

  private def formatMap(tag: String, data: Map[String,String])(implicit req: Request[AnyContent]) = {
    getOutputType(req) match {
      case o: TextOutput =>
        Ok("text - " + tag).as(o.contentType)
      case o: BashOutput =>
        Ok("bash - " + tag).as(o.contentType)
      case o =>
        Ok("json - " + tag).as(o.contentType)
    }
  }

  def getOutputType(request: Request[AnyContent]): OutputType = OutputType(request) match {
    case Some(ot) => ot
    case None => defaultOutputType
  }
}
