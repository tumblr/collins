package com.tumblr.play

import play.api.{Application, Configuration, Logger, PlayException, Plugin}
import com.twitter.util.Future
import scala.collection.immutable.SortedSet

// Token is used for looking up an asset, notify is the address to use for notification
// Profile would be the same as the profile identifier
case class ProvisionerRequest(token: String, profile: ProvisionerProfile, notification: Option[String] = None)
case class ProvisionerProfile(identifier: String, label: String) extends Ordered[ProvisionerProfile] {
  override def compare(that: ProvisionerProfile): Int = {
    this.label.compare(that.label)
  }
}

trait ProvisionerInterface {
  protected[this] val logger = Logger(getClass)
  def profiles: Set[ProvisionerProfile]
  def canProvision(thing: {def status: Int}): Boolean
  def provision(request: ProvisionerRequest): Future[Int]
  def profile(id: String): Option[ProvisionerProfile] = {
    profiles.find(_.identifier == id)
  }
  def makeRequest(token: String, id: String, notification: Option[String] = None): Option[ProvisionerRequest] = {
    profile(id).map { p =>
      ProvisionerRequest(token, p, notification)
    }
  }
}

class ProvisionerPlugin(app: Application) extends Plugin with ProvisionerInterface {
  protected[this] val configuration: Option[Configuration] = app.configuration.getConfig("provisioner")
  protected[this] val commandTemplate: Option[String] = configuration.flatMap(_.getString("command"))
  protected[this] val InvalidConfig = PlayException(
    "Invalid Configuration",
    "provisioner.enabled is true but provisioner.command not specified",
    None
  )
  // FIXME this should be based on configs
  protected[this] val allowedStatus: Set[Int] = Set(1,2,3,4,5,6,7)

  override def enabled: Boolean = {
    configuration.flatMap { cfg =>
      cfg.getBoolean("enabled")
    }.getOrElse(false)
  }

  override def onStart() {
    if (enabled && !commandTemplate.isDefined) {
      throw InvalidConfig
    }
  }

  override lazy val profiles: Set[ProvisionerProfile] =
    profiles(configuration)

  override def canProvision(thing: {def status: Int}): Boolean = {
    allowedStatus.contains(thing.status)
  }

  override def provision(request: ProvisionerRequest): Future[Int] = {
    Future(0)
  }

  protected def profiles(config: Option[Configuration]): Set[ProvisionerProfile] = {
    config.flatMap { cfg =>
      cfg.getConfig("profiles").map { p =>
        SortedSet(p.keys.toSeq.map { key =>
          ProvisionerProfile(key, p.getString(key).get)
        }:_*)
      }
    }.getOrElse(Set())
  }

  protected def command(request: ProvisionerRequest): String = {
    commandTemplate.map { cmd =>
      cmd.replace("<tag>", request.token)
        .replace("<profile-id>", request.profile.identifier)
        .replace("<notify>", request.notification.getOrElse(""))
    }.getOrElse {
      throw InvalidConfig
    }
  }

}
