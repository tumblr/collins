package controllers
package actions
package asset

import forms._

import collins.validation.StringUtil
import models.{Asset, AssetLifecycle, AssetType, IpmiInfo, Status => AssetStatus, Truthy}
import util.OutputType
import util.security.SecuritySpecification

import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.Result

case class CreateAction(
  _assetTag: Option[String],
  _assetType: Option[String],
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction {

  case class ActionDataHolder(
    assetTag: String,
    generateIpmi: Boolean,
    assetType: AssetType,
    assetStatus: Option[AssetStatus]
  ) extends RequestDataHolder

  lazy val dataHolder: Either[RequestDataHolder,ActionDataHolder] = Form(tuple(
    "generate_ipmi" -> optional(of[Truthy]),
    "type" -> optional(of[AssetType]),
    "status" -> optional(of[AssetStatus]),
    "tag" -> optional(text(1))
  )).bindFromRequest()(request).fold(
    err => Left(RequestDataHolder.error400(fieldError(err))),
    tuple => {
      val (generate, atype, astatus, tag) = tuple
      val assetType = _assetType.flatMap(a => AssetType.findByName(a)).orElse(atype).orElse(AssetType.ServerNode)
      val atString = assetType.map(_.name).getOrElse("Unknown")
      val assetTag = getString(_assetTag, tag)
      if (assetTag.isEmpty) {
        Left(RequestDataHolder.error400("Asset tag not specified").update("assetType", atString))
      } else if (!assetType.isDefined) {
        Left(RequestDataHolder.error400("Invalid asset type specified"))
      } else {
        Right(ActionDataHolder(
          assetTag,
          generate.map(_.toBoolean).getOrElse(AssetType.isServerNode(assetType.get)),
          assetType.get,
          astatus
        ))
      }
    }
  )

  override def validate(): Either[RequestDataHolder,RequestDataHolder] = dataHolder match {
    case Left(err) => Left(err)
    case Right(dh) => assetExists(dh.assetTag) match {
      case true =>
        Left(
          RequestDataHolder.error409("Duplicate asset tag '%s'".format(dh.assetTag))
            .update("assetType", dh.assetType.name)
        )
      case false =>
        Right(dh)
    }
  }

  override def execute(rd: RequestDataHolder) = rd match {
    case ActionDataHolder(assetTag, genIpmi, assetType, assetStatus) =>
      AssetLifecycle.createAsset(assetTag, assetType, genIpmi, assetStatus) match {
        case Left(throwable) =>
          handleError(
            RequestDataHolder.error500("Could not create asset: %s".format(throwable.getMessage))
          )
        case Right((asset, ipmi)) => handleSuccess(asset, ipmi)
      }
  }

  override def handleWebError(rd: RequestDataHolder) = if(rd.string(rd.ErrorKey).isDefined) {
    rd.string(rd.ErrorKey).map{
      err => Redirect(app.routes.Resources.index).flashing("error" -> err)
    }
  } else assetTypeString(rd) match {
    case None =>
      Some(Redirect(app.routes.Resources.index).flashing("error" -> "Invalid asset type specified"))
    case Some(s) =>
      Some(Redirect(app.routes.Resources.displayCreateForm(s)).flashing(
        "error" -> rd.error.getOrElse("A tag must be specified")
      ))
  }
    
  

  protected def handleSuccess(asset: Asset, ipmi: Option[IpmiInfo]): Result = isHtml match {
    case true =>
      Redirect(app.routes.Resources.index).flashing("success" -> "Asset successfully created")
    case false =>
      ResponseData(Status.Created, createMessage(asset, ipmi))
  }

  protected def fieldError(f: Form[_]) = f match {
    case e if e.error("generate_ipmi").isDefined => "generate_ipmi requires a boolean value"
    case e if e.error("type").isDefined => "Invalid asset type specified"
    case e if e.error("status").isDefined => "Invalid status specified"
    case e if e.error("tag").isDefined => "Asset tag must not be empty"
    case n => "Unexpected error occurred"
  }

  protected def assetTypeString(rd: RequestDataHolder): Option[String] = rd match {
    // FIXME ServerNode not a valid create type via UI
    case ActionDataHolder(_, _, at, _) => Some(at.name)
    case s if s.string("assetType").isDefined => s.string("assetType")
    case o => None
  }

  protected def createMessage(asset: Asset, ipmi: Option[IpmiInfo]) = {
    val as = Seq("ASSET" -> asset.toJsValue)
    ipmi.map(i => i.withExposedCredentials(true).toJsValue) match {
      case None => as
      case Some(js) => as ++ Seq("IPMI" -> js)
    }
  }

  protected def getString(pri: Option[String], sec: Option[String]): String = {
    val pris = pri.flatMap(StringUtil.strip(_))
    val secs = sec.flatMap(StringUtil.strip(_))
    if (pris.isDefined)
      pris.get
    else if (secs.isDefined)
      secs.get
    else
      ""
  }
}

