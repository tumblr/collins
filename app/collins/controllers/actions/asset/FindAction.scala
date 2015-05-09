package collins.controllers.actions.asset

import java.util.concurrent.TimeoutException

import collins.controllers.SecureController
import collins.controllers.actions.AssetAction
import collins.controllers.actions.AssetResultsAction
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.models.Asset
import collins.models.shared.PageParams
import collins.util.config.MultiCollinsConfig
import collins.util.security.SecuritySpecification

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
      case e: Throwable =>
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
        case e: Throwable =>
          logger.error("Error finding assets: %s".format(e.getMessage), e)
          handleError(RequestDataHolder.error500(
            "Error executing search: " + e.getMessage
          ))
      }
  }

}
