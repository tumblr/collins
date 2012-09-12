package controllers
package actions
package asset

import actors._
import forms._
import models.{Asset, AssetLifecycle, Truthy}
import models.Status.Enum.{New => NewAsset}
import util.{ApiTattler, UserTattler}
import util.concurrent.BackgroundProcessor
import util.config.Feature
import util.plugins.SoftLayer

import play.api.data._
import play.api.data.Forms._
import play.api.libs.concurrent.{Akka, Promise}
import play.api.mvc._

import collins.provisioning.{ProvisionerPlugin, ProvisionerProfile, ProvisionerRequest}
import collins.provisioning.{ProvisionerRoleData => ProvisionerRole}

trait ProvisionUtil { self: SecureAction =>

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
        "contact" -> text(3),
        "suffix" -> optional(text(3)),
        "primary_role" -> optional(text),
        "pool" -> optional(text),
        "secondary_role" -> optional(text),
        "activate" -> optional(of[Truthy])
      ))

  case class ActionDataHolder(
    asset: Asset, request: ProvisionerRequest, activate: Boolean, attribs: Map[String,String] = Map.empty
  ) extends RequestDataHolder

  protected def validate(plugin: ProvisionerPlugin, asset: Asset, form: ProvisionForm): Validation = {
    val activate = form._7
    if (activeBool(activate) == true)
      validateActivate(plugin, asset, form) match {
        case Some(error) =>
          return Left(error)
        case _ =>
      }
    else if (!plugin.canProvision(asset))
      return Left(
        RequestDataHolder.error403(
          "Provisioning prevented by configuration. Asset does not have allowed status"
        )
      )
    validateProvision(plugin, asset, form)
  }

  protected def validateProvision(
    plugin: ProvisionerPlugin, asset: Asset, form: ProvisionForm
  ): Validation = {
    val (profile, contact, suffix, primary_role, pool, secondary_role, activate) = form
    plugin.makeRequest(asset.tag, profile, Some(contact), suffix) match {
      case None =>
        Left(RequestDataHolder.error400("Invalid profile %s specified".format(profile)))
      case Some(request) =>
        val role = request.profile.role
        validatePrimaryRole(role, primary_role)
          .right.flatMap(vrole => validatePool(vrole, pool))
          .right.flatMap(vrole => validateSecondaryRole(vrole, secondary_role))
          .right.map(frole => request.profile.copy(role = frole))
          .right.map(profile => request.copy(profile = profile))
          .right.map { frequest =>
            ActionDataHolder(asset, frequest, activeBool(activate), attribs(frequest, form))
          }
    }
  }

  protected def validateActivate(
    plugin: ProvisionerPlugin, asset: Asset, form: ProvisionForm
  ): Option[RequestDataHolder] = {
    if (!asset.isIncomplete)
      Some(RequestDataHolder.error409("Asset status must be 'Incomplete'"))
    else if (!SoftLayer.plugin.isDefined)
      Some(RequestDataHolder.error501("SoftLayer plugin not enabled"))
    else if (!SoftLayer.plugin.get.isSoftLayerAsset(asset))
      Some(RequestDataHolder.error400("Asset not a SoftLayer asset"))
    else if (!SoftLayer.plugin.get.softLayerId(asset).isDefined)
      Some(RequestDataHolder.error400("Asset not a SoftLayer asset"))
    else
      None
  }

  protected def attribs(request: ProvisionerRequest, form: ProvisionForm): Map[String,String] = {
    val contact = form._2
    val suffix = form._3
    val role = request.profile.role
    val attribSequence =
      Seq(
        "NODECLASS" -> request.profile.identifier,
        "CONTACT" -> contact,
        "SUFFIX" -> suffix.getOrElse(""),
        "PRIMARY_ROLE" -> role.primary_role.getOrElse(""),
        "POOL" -> role.pool.getOrElse(""),
        "SECONDARY_ROLE" -> role.secondary_role.getOrElse("")
      )
    val mapForDelete = Feature.deleteSomeMetaOnRepurpose.map(_.name).map(s => (s -> "")).toMap
    mapForDelete ++ Map(attribSequence:_*)
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
    val tattler = isHtml match {
      case true => UserTattler
      case false => ApiTattler
    }
    if (error)
      tattler.critical(definedAsset, userOption, message)
    else
      tattler.note(definedAsset, userOption, message)
  }

  protected def activateAsset(adh: ActionDataHolder): Promise[Result] = {
    val ActionDataHolder(asset, pRequest, _, attribs) = adh
    val plugin = SoftLayer.plugin.get
    val slId = plugin.softLayerId(asset).get
    if (attribs.nonEmpty)
      AssetLifecycle.updateAssetAttributes(
        Asset.findById(asset.getId).get, attribs
      )
    BackgroundProcessor.send(ActivationProcessor(slId)(request)) { res =>
      processProvisionAction(res) {
        case true =>
          val newAsset = Asset.findById(asset.getId).get
          Asset.partialUpdate(newAsset, None, Some(NewAsset.id))
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

  protected def provisionAsset(adh: ActionDataHolder): Promise[Result] = {
    import play.api.Play.current

    val ActionDataHolder(asset, pRequest, _, attribs) = adh
    BackgroundProcessor.send(ProvisionerTest(pRequest)(request)) { res =>
      processProvisionAction(res) { result =>
        processProvision(result)
      }
    }.flatMap {
      case Some(err) =>
        onFailure()
        Akka.future(err)
      case None =>
        if (attribs.nonEmpty) {
          AssetLifecycle.updateAssetAttributes(
            Asset.findById(asset.getId).get, attribs
          )
          setAsset(Asset.findById(asset.getId))
        }
        BackgroundProcessor.send(ProvisionerRun(pRequest)(request)) { res =>
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

  type BackgroundResult[T] = BackgroundProcessor.SendType[T]
  type ErrorCheck = Option[Result]
  protected def processProvisionAction[T, A](res: BackgroundResult[T])(f: T => ErrorCheck): ErrorCheck = res match {
    case (Some(ex), _) =>
      tattle("Exception provisioning asset: %s".format(ex.getMessage), true)
      logger.error("Exception provisioning %s".format(getAsset), ex)
      Some(handleError(RequestDataHolder.error500(
        "There was an exception processing your request: %s".format(ex.getMessage),
        ex
      )))
    case (_, result) => result match {
      case None =>
        tattle("Timeout provisioning asset", true)
        Some(handleError(RequestDataHolder.error504("Timeout provisioning asset")))
      case Some(value) =>
        f(value)
    }
  }

  protected def processProvision(result: ProvisionerResult): ErrorCheck = result match {
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
