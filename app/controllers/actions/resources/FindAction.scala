package controllers
package actions
package resources

import asset.{AssetFinderDataHolder, FindAction => AssetFindAction}

import models.{Asset, Page, PageParams}
import models.asset.AssetView
import util.security.SecuritySpecification

import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.Result

case class FindAction(
  pageParams: PageParams,
  operation: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends AssetFindAction(pageParams, spec, handler) {

  override def validate(): Either[RequestDataHolder,RequestDataHolder] =  
    AssetFinderDataHolder.processRequest(ActionHelper.createRequest(request, requestMap))

  override def execute(rd: RequestDataHolder) = rd match {
    case adh: AssetFinderDataHolder =>
      super.execute(rd)
  }

  // WARNING - Do not change the logic around the Redirect to intake without knowing what it does
  override protected def handleWebSuccess(p: Page[AssetView], details: Boolean): Result = {
    p.size match {
      case 0 =>
        Redirect(app.routes.Resources.index).flashing("message" -> AssetMessages.noMatch)
      case 1 =>
        val asset = p.items(0)
        val ignores = Set("operation")
        val rmap = requestMap.filterNot(kv => ignores.contains(kv._1))
        // If the only thing specified was the tag, and intake is allowed, go through intake
        if (rmap.contains("tag") && rmap.size == 1 && assetIntakeAllowed(asset).isEmpty)
          Redirect(app.routes.Resources.intake(asset.id, 1))
        else
          Status.Redirect(asset.remoteHost.getOrElse("") + app.routes.CookieApi.getAsset(p.items(0).tag))
      case n =>
        Status.Ok(views.html.asset.list(p, pageParams.sort, Some(pageParams.sortField))(flash, request))
    }
  }

  override def handleWebError(rd: RequestDataHolder): Option[Result] = Some(
    Redirect(app.routes.Resources.index).flashing("error" -> rd.toString)
  )

  private[FindAction] class RichRequestMap(val underlying: Map[String,Seq[String]]) {
    val PageParamKeys = Set("page", "sort", "size", "sortfield")
    val FormKeys: Set[String] = AssetFinderDataHolder.finderForm.mapping.mappings.map(_.key).toSet

    // Remove any values in the request map that have an empty element
    def removeEmptyValues(): RichRequestMap = new RichRequestMap(
      underlying.filter { case(k, vals) =>
        vals.forall(s => s.nonEmpty)
      }
    )

    // Remove pagination parameters
    def removePaginationParameters(): RichRequestMap = new RichRequestMap(
      underlying.filter { case(k, vals) =>
        !PageParamKeys.contains(k.toLowerCase)
      }
    )

    // convert values with magic key of (none) to ""
    def convertMagicValues(): RichRequestMap = new RichRequestMap(
      underlying.map { case(k, vals) =>
        k -> vals.map { v =>
          if (v.toLowerCase == "(none)")
            ""
          else
            v
        }
      }
    )

    // rewrite appropriate keys into attribute key
    def rewriteAttributes(): RichRequestMap = new RichRequestMap(
      underlying.foldLeft(Map[String,Seq[String]]()) { case (rewritten, item) =>
        val (key, vals) = item
        FormKeys.contains(key) match {
          case true =>
            // valid form key, use it as is
            rewritten.updated(key, vals)
          case false =>
            // invalid form key, probably an attribute, rewrite it
            val rewrittenVals = vals.map(v => "%s;%s".format(key, v))
            val attribs = rewritten.get("attribute").getOrElse(Seq())
            rewritten.updated("attribute", attribs ++ rewrittenVals)
        }
      }
    )

    def replaceParam(k: String, v: String): RichRequestMap = new RichRequestMap(
      underlying.updated(k, Seq(v))
    )
  }
  implicit def m2rrm(m: Map[String,Seq[String]]) = new RichRequestMap(m)

  protected def requestMap(): Map[String,Seq[String]] = {
    request.queryString
      .removeEmptyValues
      .removePaginationParameters
      .convertMagicValues
      .rewriteAttributes
      .replaceParam("operation", operation)
      .underlying
  }

}
