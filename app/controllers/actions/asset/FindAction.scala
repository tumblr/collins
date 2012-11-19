package controllers
package actions
package asset

import forms._

import models.{Asset, AssetType, AssetFinder, Page, PageParams, State, Status => AssetStatus, Truthy}
import models.asset.AssetView

import util.AttributeResolver
import util.config.MultiCollinsConfig
import util.security.SecuritySpecification

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
) extends SecureAction(spec, handler) with AssetAction with AssetResultsAction {

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
        val results = if (MultiCollinsConfig.enabled && rl.map(_.isTruthy).getOrElse(false)) {
          logger.debug("Performing remote asset find")
          Asset.findMulti(pageParams, ra, af, op, de.map(_.isTruthy).getOrElse(true) || isHtml)
        } else {
          logger.debug("Performing local asset find")
          Asset.find(pageParams, ra, af, op)
        }
        handleSuccess(results, afdh.details.map(_.isTruthy).getOrElse(true)) 
      } catch {
        case timeout: TimeoutException => {
          handleError(RequestDataHolder.error504(
            "Error executing search: " + timeout.getMessage
          ))
        }
        case e =>
          logger.error("Error finding assets: %s".format(e.getMessage), e)
          handleError(RequestDataHolder.error500(
            "Error executing search: " + e.getMessage
          ))
      }
  }

}
