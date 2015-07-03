package collins.controllers.actions.asset

import scala.concurrent.Future

import play.api.data.Form
import play.api.data.Forms.of
import play.api.data.Forms.optional
import play.api.data.Forms.text
import play.api.data.Forms.tuple
import play.api.mvc.Result
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import collins.controllers.ResponseData
import collins.controllers.SecureController
import collins.controllers.actions.AssetAction
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.controllers.forms.statusFormat
import collins.controllers.forms.truthyFormat
import collins.controllers.forms.typeFormat
import collins.models.Asset
import collins.models.AssetLifecycle
import collins.models.AssetType
import collins.models.IpmiInfo
import collins.models.{Status => AssetStatus}
import collins.models.Truthy
import collins.util.security.SecuritySpecification
import collins.validation.StringUtil

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

  override def execute(rd: RequestDataHolder) = Future { rd match {
    case ActionDataHolder(assetTag, genIpmi, assetType, assetStatus) =>
      val lifeCycle = new AssetLifecycle(userOption(), tattler)
      lifeCycle.createAsset(assetTag, assetType, genIpmi, assetStatus) match {
        case Left(throwable) =>
          handleError(
            RequestDataHolder.error500("Could not create asset: %s".format(throwable.getMessage))
          )
        case Right((asset, ipmi)) => handleSuccess(asset, ipmi)
      }
    }
  }

  override def handleWebError(rd: RequestDataHolder) = if(rd.string(rd.ErrorKey).isDefined) {
    rd.string(rd.ErrorKey).map{
      err => Redirect(collins.app.routes.Resources.index).flashing("error" -> err)
    }
  } else assetTypeString(rd) match {
    case None =>
      Some(Redirect(collins.app.routes.Resources.index).flashing("error" -> "Invalid asset type specified"))
    case Some(s) =>
      Some(Redirect(collins.app.routes.Resources.displayCreateForm(s)).flashing(
        "error" -> rd.error.getOrElse("A tag must be specified")
      ))
  }
    
  

  protected def handleSuccess(asset: Asset, ipmi: Option[IpmiInfo]): Result = isHtml match {
    case true =>
      Redirect(collins.app.routes.Resources.index).flashing("success" -> "Asset successfully created")
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

