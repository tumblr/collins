package controllers
package actions

import forms._
import models.{Asset, AssetLifecycle, AssetType, IpmiInfo, Status => AStatus}

import play.api.data._
import play.api.http.{Status => StatusValues}
import play.api.libs.json._
import play.api.mvc._

private[controllers] object CreateAsset {
  val params = Set("generate_ipmi", "type", "status")
  val createForm = Form(of(
    "generate_ipmi" -> optional(boolean),
    "type" -> optional(of[AssetType.Enum]),
    "status" -> optional(of[AStatus.Enum])
  ))
}

// FIXME: Don't extend ApiResponse, have apply return standard Either
private[controllers] class CreateAsset() {
  val defaultAssetType = AssetType.Enum.ServerNode

  type Success = (Option[Boolean],AssetType,Option[AStatus.Enum])
  def validateRequest()(implicit request: Request[AnyContent]): Either[String,Success] = {
    CreateAsset.createForm.bindFromRequest.fold(
      err => {
        val errors = CreateAsset.params.map { p =>
          err(p).error.map { _ =>
            p match {
              case "generate_ipmi" =>
                "%s only takes true or false as values".format(p)
              case "type" =>
                "Invalid asset type specified"
              case "status" =>
                "Invalid status specified"
            }
          }
        }.filter { _.isDefined }.map { _.get }
        val msg = errors.isEmpty match {
          case true => "Error during parameter validation"
          case false => errors.mkString(", ")
        }
        Left(msg)
      },
      success => {
        val gen_ipmi = success._1
        val atype = AssetType.fromEnum(success._2.getOrElse(defaultAssetType))
        val status = success._3
        Right((gen_ipmi, atype, status))
      }
    )
  }

  def validateTag(tag: String): Option[ResponseData] = {
    Asset.isValidTag(tag) match {
      case false => Some(Api.getErrorMessage("Invalid tag specified"))
      case true => Asset.findByTag(tag) match {
        case Some(asset) =>
          val msg = "Asset with tag '%s' already exists".format(tag)
          Some(Api.getErrorMessage(msg, Results.Status(StatusValues.CONFLICT)))
        case None => None
      }
    }
  }

  protected def getCreateMessage(asset: Asset, ipmi: Option[IpmiInfo]): JsObject = {
    val seq = ipmi.map { ipmi_info =>
        Seq("ASSET" -> JsObject(asset.forJsonObject),
            "IPMI" -> JsObject(ipmi_info.withExposedCredentials(true).forJsonObject))
    }.getOrElse(Seq("ASSET" -> JsObject(asset.forJsonObject)))
    JsObject(seq)
  }

  def apply(tag: String)(implicit req: Request[AnyContent]) = {
    validateTag(tag) match {
      case Some(data) => data
      case None => validateRequest() match {
        case Left(error) => Api.getErrorMessage(error)
        case Right((optGenerateIpmi, assetType, status)) =>
          val generateIpmi = optGenerateIpmi.getOrElse({
            assetType.getId == AssetType.Enum.ServerNode.id
          })
          AssetLifecycle.createAsset(tag, assetType, generateIpmi, status) match {
            case Left(ex) => Api.getErrorMessage(ex.getMessage)
            case Right((asset, ipmi)) =>
              ResponseData(Results.Created, getCreateMessage(asset, ipmi))
          }
      }
    }
  }
}
