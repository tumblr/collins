package collins.controllers.actions.logs

import scala.concurrent.Future

import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist

import play.api.data.Form
import play.api.data.Forms.tuple
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsUndefined
import play.api.libs.json.JsValue
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import collins.controllers.Api
import collins.controllers.ResponseData
import collins.controllers.SecureController
import collins.controllers.actions.AssetAction
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.controllers.validators.ParamValidation
import collins.models.Asset
import collins.models.AssetLog
import collins.models.logs.LogFormat
import collins.models.logs.LogMessageType
import collins.models.logs.LogSource
import collins.util.MessageHelper
import collins.util.config.Feature
import collins.util.security.SecuritySpecification

object CreateAction {
  val DefaultMessageType = Feature.defaultLogType
  val ValidMessageSources = LogSource.values.mkString(", ")
  val ValidMessageTypes = LogMessageType.values.mkString(", ")
  object Messages extends MessageHelper("assetlog.create") {
    val MessageError = messageWithDefault("error.messageInvalid", "Message must not be empty")
    val MessageTypeError = messageWithDefault(
      "error.messageTypeInvalid", "Message type is invalid", ValidMessageTypes
    )
    val MessageSourceError = messageWithDefault(
      "error.messageSourceInvalid", "Message source is invalid", ValidMessageSources
    )
  }
}

case class CreateAction(
  assetTag: String,
  defaultMessageType: LogMessageType.LogMessageType = CreateAction.DefaultMessageType,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction with ParamValidation {

  import CreateAction.Messages._

  type DataForm = Tuple3[String,Option[String],Option[String]]
  type FilledForm = Tuple3[String,LogSource.LogSource,LogMessageType.LogMessageType]
  val dataForm: Form[DataForm] = Form(tuple(
    "message" -> validatedText(1),
    "source" -> validatedOptionalText(1),
    "type" -> validatedOptionalText(1)
  ))

  case class ActionDataHolder(log: AssetLog) extends RequestDataHolder

  override def validate(): Validation = {
    withValidAsset(assetTag) { asset =>
      request.body.asJson match {
        case Some(js) => validateJson(asset, js)
        case None => validateForm(asset)
      }
    }
  }

  override def execute(rdh: RequestDataHolder) = Future {
    rdh match {
      case ActionDataHolder(log) =>
        AssetLog.create(log) match {
          case ok if ok.id > 0 =>
            ResponseData(Status.Created, Seq("SUCCESS" -> JsBoolean(true), "Data" -> log.toJsValue()))
          case bad =>
            Api.statusResponse(false, Status.BadRequest)
        }
    }
  }

  // Validate a submitted form
  protected def validateForm(asset: Asset): Validation = {
    dataForm.bindFromRequest()(request).fold(
      error => Left(RequestDataHolder.error400(MessageError)),
      success => logFromRequestData(success) { case(messageBody, messageSource, messageType) =>
        val msg = formatStringMessage(messageBody)
        AssetLog(asset, userOption().map { _.username }.getOrElse(""), msg, LogFormat.PlainText, messageSource, messageType)
      }
    )
  }

  // Validate a JSON blob submitted
  protected def validateJson(asset: Asset, js: JsValue): Validation = {
    val otype = (js \ "Type").asOpt[String]
    val omessage = (js \ "Message") match {
      case _: JsUndefined => None
      case other => Some(other.toString) // Want string representation of JSON
    }
    val osource = None
    omessage match {
      case None => Left(RequestDataHolder.error400(MessageError))
      case Some(message) =>
        logFromRequestData(message, osource, otype) { case(messageBody,messageSource,messageType) =>
          AssetLog(asset, userOption().map { _.username }.getOrElse(""), messageBody, LogFormat.Json, messageSource, messageType)
        }
    }
  }

  // Given a dataForm, if it's valid pass it to a converter function that returns an AssetLog
  // If it's invalid, handle the various failure cases
  protected def logFromRequestData(data: DataForm)(f: FilledForm => AssetLog): Validation = {
    extractLogData(data) match {
      case (messageBody, Some(messageSource), Some(messageType)) =>
        Right(ActionDataHolder(f(messageBody, messageSource, messageType)))
      case (_, _, None) =>
        Left(RequestDataHolder.error400(MessageTypeError))
      case (_, None, _) =>
        Left(RequestDataHolder.error400(MessageSourceError))
    }
  }

  // Given a text message, strip out bad HTML and include the user in the text
  protected def formatStringMessage(txt: String): String = {
      Jsoup.clean(txt, Whitelist.basic())
  }

  // Given some form data provide defaults and convert things to None when invalid
  protected def extractLogData(form: DataForm) = {
    val (messageBody, messageSource, messageType) = form
    val filteredMessageType = messageType.orElse(Some(defaultMessageType.toString))
                                .flatMap(convertMessageType(_))
    val filteredMessageSource = messageSource.map(_ => "USER").orElse(Some("API"))
                                  .flatMap(convertMessageSource(_))
    (messageBody, filteredMessageSource, filteredMessageType)
  }

  // The below methods convert strings to their appropriate types, or None if invalid
  protected def convertMessageType(s: String) = convertLogEnum(s) { e =>
    LogMessageType.withName(e)
  }
  protected def convertMessageSource(s: String) = convertLogEnum(s) { e =>
    LogSource.withName(e)
  }
  protected def convertLogEnum[T](s: String)(f: String => T): Option[T] = {
    try {
      Some(f(s.toUpperCase))
    } catch {
      case e: Throwable => None
    }
  }

}
