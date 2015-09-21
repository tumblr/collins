package collins.controllers.actions.asset

import scala.concurrent.Future

import play.api.data.Form
import play.api.data.Forms.of
import play.api.data.Forms.optional
import play.api.data.Forms.text
import play.api.data.Forms.tuple
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.Result

import collins.controllers.Api
import collins.controllers.actions.AssetAction
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.controllers.actors.ActivationProcessor
import collins.controllers.actors.ProvisionerResult
import collins.controllers.actors.ProvisionerRun
import collins.controllers.actors.ProvisionerTest
import collins.controllers.forms.truthyFormat
import collins.models.Asset
import collins.models.AssetLifecycle
import collins.models.AssetMetaValue
import collins.models.{ Status => AssetStatus }
import collins.models.Truthy
import collins.provisioning.Provisioner
import collins.provisioning.ProvisionerRequest
import collins.provisioning.{ ProvisionerRoleData => ProvisionerRole }
import collins.softlayer.SoftLayer
import collins.softlayer.SoftLayerConfig
import collins.util.concurrent.BackgroundProcessor
import collins.util.concurrent.BackgroundProcessor.SendType
import collins.util.config.Feature

trait ProvisionUtil { self: SecureAction =>
  import collins.controllers.forms._

  type ProvisionForm = Tuple7[
    String, // profile
    String, // contact
    Option[String], // suffix
    Option[String], // profile role
    Option[String], // pool
    Option[String], // secondary_role
    Option[Truthy] // active
  ]

  val provisionForm = Form(tuple(
        "profile" -> text,
        "contact" -> text,
        "suffix" -> optional(text(3)),
        "primary_role" -> optional(text),
        "pool" -> optional(text),
        "secondary_role" -> optional(text),
        "activate" -> optional(of[Truthy])
      ))

  case class ActionDataHolder(
    asset: Asset, request: ProvisionerRequest, activate: Boolean, attribs: Map[String,String] = Map.empty
  ) extends RequestDataHolder

  protected def validate(asset: Asset, form: ProvisionForm): Validation = {
    val activate = form._7
    if (activeBool(activate) == true)
      validateActivate(asset, form) match {
        case Some(error) =>
          return Left(error)
        case _ =>
      }
    else if (!Provisioner.canProvision(asset))
      return Left(
        RequestDataHolder.error403(
          "Provisioning prevented by configuration. Asset does not have allowed status"
        )
      )
    validateProvision(asset, form)
  }

  protected def validateProvision(
    asset: Asset, form: ProvisionForm
  ): Validation = {
    val (profile, contact, suffix, primary_role, pool, secondary_role, activate) = form
    Provisioner.makeRequest(asset.tag, profile, Some(contact), suffix) match {
      case None =>
        Left(RequestDataHolder.error400("Invalid profile %s specified".format(profile)))
      case Some(request) =>
        val role = request.profile.role
        validatePrimaryRole(role, primary_role)
          .right.flatMap(vrole => validatePool(vrole, pool))
          .right.flatMap(vrole => validateSecondaryRole(vrole, secondary_role))
          .right.flatMap(vrole => validateAllowedHardware(vrole, asset))
          .right.map(frole => request.profile.copy(role = frole))
          .right.map(profile => request.copy(profile = profile))
          .right.map { frequest =>
            ActionDataHolder(asset, frequest, activeBool(activate), attribsToDelete(asset, frequest, form))
          }
    }
  }

  protected def validateActivate(
    asset: Asset, form: ProvisionForm
  ): Option[RequestDataHolder] = {
    if (!asset.isIncomplete)
      Some(RequestDataHolder.error409("Asset status must be 'Incomplete'"))
    else if (!SoftLayerConfig.enabled)
      Some(RequestDataHolder.error501("SoftLayer plugin not enabled"))
    else if (!SoftLayer.isSoftLayerAsset(asset))
      Some(RequestDataHolder.error400("Asset not a SoftLayer asset"))
    else if (!SoftLayer.softLayerId(asset).isDefined)
      Some(RequestDataHolder.error400("Asset not a SoftLayer asset"))
    else
      None
  }

  protected def attribsToDelete(asset: Asset, request: ProvisionerRequest, form: ProvisionForm): Map[String,String] = {
    val build_contact = form._2
    val suffix = form._3
    val role = request.profile.role
    val highPriorityAttrs =
      Map(
        "NODECLASS" -> request.profile.identifier,
        "CONTACT" -> role.contact.getOrElse(""),
        "CONTACT_NOTES" -> role.contact_notes.getOrElse(""),
        "SUFFIX" -> suffix.getOrElse(""),
        "PRIMARY_ROLE" -> role.primary_role.getOrElse(""),
        "POOL" -> role.pool.getOrElse(""),
        "SECONDARY_ROLE" -> role.secondary_role.getOrElse(""),
        "BUILD_CONTACT" -> build_contact
      )
    val lowPriorityAttrs = role.attributes
    val clearProfileAttrs = role.clear_attributes.map(_ -> "").toMap
    val clearOnRepurposeAttrs = Feature.deleteSomeMetaOnRepurpose.map(_ -> "").toMap
    val keepOnRepurposeAttrs = Feature.keepSomeMetaOnRepurpose

    // make sure high priority attrs take precedence over low priority
    // and make sure any explicitly set attrs override any that are to be cleared
    if (Feature.useWhiteListOnRepurpose) {
      clearOnRepurposeAttrs ++ clearProfileAttrs ++ lowPriorityAttrs ++ highPriorityAttrs -- keepOnRepurposeAttrs
    } else {
      clearOnRepurposeAttrs ++ clearProfileAttrs ++ lowPriorityAttrs ++ highPriorityAttrs
    }
  }

  protected def fieldError(form: Form[ProvisionForm]): Validation = (form match {
    case f if f.error("profile").isDefined => Option("Profile must be specified")
    case f if f.error("contact").isDefined => Option("Contact must be specified")
    case f if f.error("suffix").isDefined => Option("Suffix must be at least 3 characters")
    case f if f.error("primary_role").isDefined => Option("Invalid primary_role")
    case f if f.error("pool").isDefined => Option("Invalid pool specified")
    case f if f.error("secondary_role").isDefined => Option("Invalid secondary_role")
    case f if f.error("activate").isDefined => Option("activate must be truthy")
    case o => None
  }).map(s => Left(RequestDataHolder.error400(s)))
    .getOrElse(Left(RequestDataHolder.error400("An unknown error occurred")))

  private def activeBool(activate: Option[Truthy]) = activate.map(_.toBoolean).getOrElse(false)

  type ValidOption = Either[RequestDataHolder,ProvisionerRole]
  protected def validatePrimaryRole(role: ProvisionerRole, prole: Option[String]): ValidOption = {
    if (role.primary_role.isDefined)
      Right(role)
    else if (prole.isDefined)
      Right(role.copy(primary_role = prole.map(_.toUpperCase)))
    else if (role.requires_primary_role)
      Left(RequestDataHolder.error400("A primary_role is required but none was specified"))
    else
      Right(role)
  }

  protected def validateAllowedHardware(role: ProvisionerRole, asset: Asset): ValidOption = role.allowed_classes match {
    case Some(classifiers) => asset.nodeClass match {
      case Some(nc) => {
        if (classifiers contains nc.tag)
          Right(role)
        else
          Left(RequestDataHolder.error400("Asset is classified as %s, but the provisioning profile requires assets matching: %s".format(nc.tag, classifiers.mkString(" or "))))
      }
      case _ => Left(RequestDataHolder.error400("Asset is unclassified, but the provisioning profile requires classified assets matching: %s".format(classifiers.mkString(" or "))))
    }
    case _ => Right(role)
  }

  protected def validateSecondaryRole(role: ProvisionerRole, srole: Option[String]): ValidOption = {
    if (role.secondary_role.isDefined)
      Right(role)
    else if (srole.isDefined)
      Right(role.copy(secondary_role = srole.map(_.toUpperCase)))
    else if (role.requires_secondary_role)
      Left(RequestDataHolder.error400("A secondary_role is required but none was specified"))
    else
      Right(role)
  }

  protected def validatePool(role: ProvisionerRole, pool: Option[String]): ValidOption = {
    if (role.pool.isDefined)
      Right(role)
    else if (pool.isDefined)
      Right(role.copy(pool = pool.map(_.toUpperCase)))
    else if (role.requires_pool)
      Left(RequestDataHolder.error400("A pool is required but none was specified"))
    else
      Right(role)
  }
}

trait Provisions extends ProvisionUtil with AssetAction { self: SecureAction =>

  protected def onSuccess() {
    // Hook for rate limiting if needed
  }
  protected def onFailure() {
    // additional hook
  }

  protected def tattle(message: String, error: Boolean) {
    if (error)
      tattler.critical(message, definedAsset)
    else
      tattler.note(message, definedAsset)
  }

  protected def activateAsset(adh: ActionDataHolder): Future[Result] = {
    val ActionDataHolder(asset, pRequest, _, attribs) = adh
    val slId = SoftLayer.softLayerId(asset).get
    if (attribs.nonEmpty) {
      val lifeCycle = new AssetLifecycle(userOption(), tattler)
      lifeCycle.updateAssetAttributes(Asset.findById(asset.id).get, attribs)
    }

    BackgroundProcessor.send(ActivationProcessor(slId)(request)) { res =>
      processProvisionAction(res) {
        case true =>
          val newAsset = Asset.findById(asset.id).get
          Asset.partialUpdate(newAsset, None, AssetStatus.New.map(_.id))
          setAsset(newAsset)
          tattle("Asset successfully activated", false)
          None
        case false =>
          tattle("Asset activation failed", true)
          onFailure()
          Some(handleError(RequestDataHolder.error400("Asset activation failed")))
      }.getOrElse {
        onSuccess()
        Api.statusResponse(true)
      }
    }
  }

  protected def provisionAsset(adh: ActionDataHolder): Future[Result] = {
    import play.api.Play.current

    val ActionDataHolder(asset, pRequest, _, attribs) = adh
    BackgroundProcessor.send(ProvisionerTest(pRequest)) { res =>
      processProvisionAction(res) { result =>
        processProvision(result)
      }
    }.flatMap {
      case Some(err) =>
        onFailure()
        Future(err)
      case None =>
        if (attribs.nonEmpty) {
          val lifeCycle = new AssetLifecycle(userOption(), tattler)
          lifeCycle.updateAssetAttributes(
            Asset.findById(asset.id).get, attribs
          )
          setAsset(Asset.findById(asset.id))
        }
        BackgroundProcessor.send(ProvisionerRun(pRequest)) { res =>
          processProvisionAction(res) { result =>
            processProvision(result).map { err =>
              tattle("Provisioning failed. Exit code %d\n%s".format(result.commandResult.exitCode,
                result.commandResult.toString
              ), true)
              onFailure()
              err
            }.orElse {
              tattle(
                "Successfully provisioned server as %s".format(pRequest.profile.identifier), false
              )
              None
            }
          }.getOrElse {
            onSuccess()
            Api.statusResponse(true)
          }
        }
    }
  }

  protected def processProvisionAction[T, A](res: SendType[T])(f: T => Option[Result]): Option[Result] = res match {
    case Left(ex) =>
      tattle("Exception provisioning asset: %s".format(ex.getMessage), true)
      logger.error("Exception provisioning %s".format(getAsset), ex)
      Some(handleError(RequestDataHolder.error500(
        "There was an exception processing your request: %s".format(ex.getMessage),
        ex
      )))
    case Right(result) => f(result)
  }

  protected def processProvision(result: ProvisionerResult): Option[Result] = result match {
    case success if success.commandResult.exitCode == 0 =>
      None
    case failure if failure.commandResult.exitCode != 0 =>
      Some(handleError(RequestDataHolder.error500(
        "There was an error processing your request. Exit Code %d".format(
          failure.commandResult.exitCode
        ), new Exception(failure.commandResult.toString)
      )))
  }

}
