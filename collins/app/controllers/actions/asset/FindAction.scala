package controllers
package actions
package asset

import forms._
import util.Config

import models.{Asset, AssetType, AssetView, AssetFinder, Page, PageParams, RemoteAsset, Status => AssetStatus, Truthy}
import models.AssetType.{Enum => AssetTypeEnum}
import models.Status.{Enum => AssetStatusEnum}

import util.{AttributeResolver, SecuritySpecification}

import play.api.libs.json._
import play.api.mvc.Result

import java.util.concurrent.TimeoutException

object FindAction {
  def apply(pageParams: PageParams, spec: SecuritySpecification, handler: SecureController) = {
    new FindAction(pageParams, spec, handler)
  }
}

class FindAction(
  pageParams: PageParams,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction {

  override def validate(): Either[RequestDataHolder,RequestDataHolder] = {
    try {
      pageParams.validate()
      AssetFinderDataHolder.processRequest(request())
    } catch {
      case e =>
        Left(RequestDataHolder.error400(e.getMessage))
    }
  }

  override def execute(rd: RequestDataHolder) = rd match {
    case afdh: AssetFinderDataHolder =>
      val AssetFinderDataHolder(af, ra, op, de, rl) = afdh
      try {
        val results = if (Config.getBoolean("multicollins.enabled").getOrElse(false) && rl.map{_.isTruthy}.getOrElse(false)) {
          logger.debug("Performing remote asset find")
          Asset.findMulti(pageParams, ra, af, op, de.map{_.isTruthy}.getOrElse(false) || isHtml)
        } else {
          logger.debug("Performing local asset find")
          Asset.find(pageParams, ra, af, op)
        }
        handleSuccess(results, afdh) 
      } catch {
        case timeout: TimeoutException => {
          handleError(RequestDataHolder.error504(
            "Error executing search: " + timeout.getMessage
          ))
        }
        case e =>
          e.printStackTrace
          handleError(RequestDataHolder.error500(
            "Error executing search: " + e.getMessage
          ))
      }
  }

  protected def handleSuccess(p: Page[AssetView], afdh: AssetFinderDataHolder) = isHtml match {
    case true =>
      handleWebSuccess(p, afdh)
    case false =>
      handleApiSuccess(p, afdh)
  }

  protected def handleWebSuccess(p: Page[AssetView], afdh: AssetFinderDataHolder): Result = {
    Api.errorResponse(NotImplementedError.toString, NotImplementedError.status().get)
  }

  protected def handleApiSuccess(p: Page[AssetView], afdh: AssetFinderDataHolder): Result = {
    val items = p.items.map { 
      case a: Asset => {
        afdh.details.filter(_.isTruthy).map { _ =>
          a.getAllAttributes.exposeCredentials(user.canSeePasswords).toJsonObject
        }.getOrElse {
          a.toJsonObject
        }
      }
      case v: RemoteAsset => v.toJsonObject 
    }.toList
    ResponseData(Status.Ok, JsObject(p.getPaginationJsObject() ++ Seq(
      "Data" -> JsArray(items)
    )), p.getPaginationHeaders)
  }

}

