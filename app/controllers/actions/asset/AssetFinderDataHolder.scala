package controllers
package actions
package asset

import validators.StringUtil

import forms._

import models.{AssetFinder, State, Status => AssetStatus, Truthy}
import models.AssetType.{Enum => AssetTypeEnum}

import util.{AttributeResolver, MessageHelper}
import util.AttributeResolver.{ResultTuple => ResolvedAttributes}
import util.views.Formatter.ISO_8601_FORMAT

import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.mvc.{AnyContent, Request, Result}

import java.util.Date

case class AssetFinderDataHolder(
  assetFinder: AssetFinder,
  attributes: ResolvedAttributes,
  operation: Option[String],
  details: Option[Truthy],
  remoteLookup: Option[Truthy]
) extends RequestDataHolder

object AssetFinderDataHolder extends MessageHelper("assetfinder") with AttributeHelper {
  protected[this] val logger = Logger.logger

  val Operations = Set("and","or")

  type DataForm = Tuple12[
    Option[String],           // tag
    Option[List[String]],     // attribute
    Option[String],           // operation
    Option[AssetStatus],      // asset status
    Option[AssetTypeEnum],    // asset type
    Option[Truthy],           // details
    Option[Date],             // createdAfter
    Option[Date],             // createdBefore
    Option[Date],             // updatedAfter
    Option[Date],             // updatedBefore
    Option[Truthy],           // remoteLookup,
    Option[State]             // state
  ]

  def finderForm = Form(tuple(
    "tag" -> optional(
      text(1).verifying { t => StringUtil.trim(t).isDefined }
    ),
    "attribute" -> optional(
      list(
        text.verifying(pattern("""\S+;.*""".r))
      )
    ),
    "operation" -> optional(
      text(2).verifying { txt => isValidOperation(txt) }
    ),
    "status" -> optional(of[AssetStatus]),
    "type" -> optional(of[AssetTypeEnum]),
    "details" -> optional(of[Truthy]),
    "createdAfter" -> optional(date(ISO_8601_FORMAT)),
    "createdBefore" -> optional(date(ISO_8601_FORMAT)),
    "updatedAfter" -> optional(date(ISO_8601_FORMAT)),
    "updatedBefore" -> optional(date(ISO_8601_FORMAT)),
    "remoteLookup" -> optional(of[Truthy]),
    "state" -> optional(of[State])
  ))

  def processRequest(req: Request[AnyContent]): Either[RequestDataHolder,AssetFinderDataHolder] = {
    processForm(finderForm.bindFromRequest()(req), req.queryString)
  }
    
  protected def processForm(form: Form[DataForm], data: Map[String,Seq[String]]): Either[RequestDataHolder,AssetFinderDataHolder] = {
    form.fold(
      err => Left(RequestDataHolder.error400(fieldError(err))),
      succ => try {
        Right(fromForm(succ, data))
      } catch {
        case e =>
          logger.debug("Error finding assets", e)
          Left(RequestDataHolder.error400(e.getMessage))
      }
    )
  }

  protected def fromForm(form: DataForm, data: Map[String,Seq[String]]): AssetFinderDataHolder = {
    val (
      tag, attributes, operation, astatus, atype, details, cafter, cbefore, uafter, ubefore, remoteLookup, state
    ) = form
    val attribs = AttributeResolver(mapAttributes(attributes.filter(_.nonEmpty), AttributeMap.fromMap(data)))
    val afinder = AssetFinder(tag, astatus, atype, cafter, cbefore, uafter, ubefore, state)
    AssetFinderDataHolder(
      afinder,
      attribs,
      cleanedOperation(operation),
      details,
      remoteLookup
    )
  }

  override def invalidAttributeMessage(s: String) = message("attribute.invalid", s)

  protected def isValidOperation(s: String) = Operations.contains(s.toLowerCase)

  protected def fieldError(f: Form[_]) = f match {
    case e if e.error("tag").isDefined => message("tag.invalid")
    case e if e.error("type").isDefined => message("type.invalid")
    case e if e.error("status").isDefined => rootMessage("asset.status.invalid")
    case e if e.error("createdAfter").isDefined => dateError("createdAfter")
    case e if e.error("createdBefore").isDefined => dateError("createdBefore")
    case e if e.error("updatedAfter").isDefined => dateError("updatedAfter")
    case e if e.error("updatedBefore").isDefined => dateError("updatedBefore")
    case e if e.error("attribute").isDefined => "attribute parameter must be at least 3 characters"
    case e if e.error("operation").isDefined => message("operation.invalid")
    case e if e.error("details").isDefined => rootMessage("error.truthy", "details")
    case e if e.error("remoteLookup").isDefined => rootMessage("error.truthy", "remoteLookup")
    case e if e.error("state").isDefined => rootMessage("asset.state.invalid")
    case n => "Unexpected error occurred"
  }

  protected def cleanedOperation(op: Option[String]): Option[String] = {
    op.map(_.toLowerCase).filter(Operations.contains(_))
  }

  private def dateError(field: String): String = message("date.invalid", field, ISO_8601_FORMAT)
}
