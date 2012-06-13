package controllers
package actions
package resources

import asset.{AssetFinderDataHolder, FindAction => AssetFindAction}

import models.{Asset, AssetView, Page, PageParams}
import util.SecuritySpecification

import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.Result

case class FindAction(
  pageParams: PageParams,
  operation: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends AssetFindAction(pageParams, spec, handler) {

  case class TagOnlyDataHolder(tag: String) extends RequestDataHolder

  def assetTag(): Option[String] = Form(
    "ASSET_TAG" -> nonEmptyText
  ).bindFromRequest()(request).fold(
    noTag => None,
    tagged => Some(tagged)
  )

  override def validate(): Either[RequestDataHolder,RequestDataHolder] = assetTag match {
    case Some(tag) => Right(TagOnlyDataHolder(tag))
    case None => AssetFinderDataHolder.processRequest(
      ActionHelper.createRequest(request, requestMap)
    )
  }

  override def execute(rd: RequestDataHolder) = rd match {
    case adh: TagOnlyDataHolder => findAssetByTag(adh)
    case adh: AssetFinderDataHolder =>
      super.execute(rd)
  }

  override protected def handleWebSuccess(p: Page[AssetView], details: Boolean): Result = {
    p.size match {
      case 0 =>
        Redirect(app.routes.Resources.index).flashing("message" -> AssetMessages.noMatch)
      case 1 =>
        Status.Redirect(p.items(0).remoteHost.getOrElse("") + app.routes.CookieApi.getAsset(p.items(0).tag))
      case n =>
        Status.Ok(views.html.asset.list(p)(flash, request))
    }
  }

  override def handleWebError(rd: RequestDataHolder): Option[Result] = Some(
    Redirect(app.routes.Resources.index).flashing("error" -> rd.toString)
  )

  protected def findAssetByTag(rd: TagOnlyDataHolder) = assetFromTag(rd.tag) match {
    case None => Asset.findLikeTag(rd.tag, pageParams) match {
      case notFound if notFound.size == 0 =>
        Redirect(app.routes.Resources.index).flashing("message" -> AssetMessages.noMatch)
      case found =>
        Status.Ok(views.html.asset.list(found)(flash, request))
    }
    case Some(asset) =>
      assetIntakeAllowed(asset) match {
        case None => Redirect(app.routes.Resources.intake(asset.getId, 1))
        case Some(err) => Redirect(app.routes.CookieApi.getAsset(asset.tag))
      }
  }

  private[FindAction] class RichRequestMap(val underlying: Map[String,Seq[String]]) {
    val PageParamKeys = Set("page", "sort", "size")
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
