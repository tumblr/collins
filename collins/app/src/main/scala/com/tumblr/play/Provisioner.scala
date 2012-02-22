package com.tumblr.play

import com.google.common.util.concurrent.UncheckedExecutionException
import com.twitter.util.Future
import org.yaml.snakeyaml.Yaml
import play.api.{Application, Configuration, Logger, PlayException, Plugin}

import java.io.{File, FileInputStream, InputStream}

import scala.collection.JavaConverters._
import scala.collection.immutable.SortedSet
import scala.collection.mutable.{Map => MutableMap, StringBuilder}
import scala.sys.process._

// Token is used for looking up an asset, notify is the address to use for notification
// Profile would be the same as the profile identifier
case class ProvisionerRequest(token: String, profile: ProvisionerProfile, notification: Option[String] = None)
case class ProvisionerProfile(identifier: String, label: String, prefix: String, allow_suffix: Boolean) extends Ordered[ProvisionerProfile] {
  override def compare(that: ProvisionerProfile): Int = {
    this.label.compare(that.label)
  }
}

trait ProvisionerInterface {
  protected[this] val logger = Logger(getClass)
  type ThingWithStatus = {
    def status: Int
  }
  def profiles: Set[ProvisionerProfile]
  def canProvision(thing: ThingWithStatus): Boolean
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
  protected[this] def InvalidConfig(s: Option[String] = None): Exception = PlayException(
    "Invalid Configuration",
    s.getOrElse("provisioner.enabled is true but provisioner.command or provisioner.profiles not specified"),
    None
  )
  protected[this] val cachePlugin = new CachePlugin(app, None, 30)
  protected[this] val allowedStatus: Set[Int] =
    configuration.flatMap(_.getString("allowedStatus")).getOrElse("1,2,3,5,7").split(",").map(_.toInt).toSet

  // overrides Plugin.enabled
  override def enabled: Boolean = {
    configuration.flatMap { cfg =>
      cfg.getBoolean("enabled")
    }.getOrElse(false)
  }

  // overrides Plugin.onStart
  override def onStart() {
    if (enabled) {
      cachePlugin.clear()
      logger.info("Cleared cache")
      try {
        if (!commandTemplate.isDefined || !haveProfiles) {
          throw InvalidConfig()
        }
      } catch {
        case e: UncheckedExecutionException =>
          throw e.getCause
        case e =>
          throw e
      }
    }
  }

  // overrides Plugin.onStop
  override def onStop() {
    cachePlugin.clear()
    cachePlugin.onStop()
  }

  // overrides ProvisionerInterface.profiles
  override def profiles: Set[ProvisionerProfile] = {
    cachePlugin.getOrElseUpdate("provisionerProfiles", {
      try {
        configuration.flatMap { cfg =>
          cfg.getString("profiles").map { p =>
            SortedSet((yamlFromFile(p) match {
              case map: java.util.Map[_, _] => processYaml(map.asScala)
              case n => Seq[ProvisionerProfile]()
            }):_*)
          }
        }.getOrElse(Set())
      } catch {
        case e: PlayException =>
          cachePlugin.clear()
          throw e
        case e =>
          cachePlugin.clear()
          throw InvalidConfig()
        }
    })
  }

  // overrides ProvisionerInterface.canProvision
  override def canProvision(thing: ThingWithStatus): Boolean = {
    allowedStatus.contains(thing.status)
  }

  // overrides ProvisionerInterface.provision
  override def provision(request: ProvisionerRequest): Future[Int] = {
    val cmd = command(request)
    val process = Process(cmd)
    val sb = new StringBuilder()
    val exitStatus = try {
      process ! ProcessLogger({s => sb.append(s)})
    } catch {
      case e: Throwable =>
        sb.append(e.getMessage)
        -1
    }
    logger.warn("Command output: " + sb.toString)
    Future(exitStatus)
  }

  protected def haveProfiles(): Boolean = profiles.size > 0

  protected def command(request: ProvisionerRequest): String = {
    commandTemplate.map { cmd =>
      cmd.replace("<tag>", request.token)
        .replace("<profile-id>", request.profile.identifier)
        .replace("<notify>", request.notification.getOrElse(""))
    }.getOrElse {
      throw InvalidConfig(Some("provisioner.command must be specified"))
    }
  }

  private[this] def processYaml[K,V](yaml: MutableMap[K, V]): Seq[ProvisionerProfile] = {
    def getStringValue[K,V](id: String, prof: MutableMap[K, V], keys: Set[String]): String = {
      for (entry <- prof) {
        entry match {
          case (label: String, value: String) if keys.contains(label) => return value
          case _ =>
        }
      }
      throw InvalidConfig(Some("missing %s for %s in profile yaml configuration".format(keys.mkString(" or "), id)))
    }
    def getBooleanValue[K,V](id: String, prof: MutableMap[K, V], keys: Set[String], default: Option[Boolean] = None): Boolean = {
      for (entry <- prof) {
        entry match {
          case (label: String, value: Boolean) if keys.contains(label) => return value
          case _ =>
        }
      }
      default.getOrElse {
        throw InvalidConfig(Some("missing %s for %s in profile yaml configuration".format(keys.mkString(" or "), id)))
      }
    }
    def processProfiles[K,V](profs: MutableMap[K, V]): Seq[ProvisionerProfile] = {
      profs.foldLeft(Seq[ProvisionerProfile]()) { case (total, current) =>
        current match {
          case (id: String, value: java.util.Map[_, _]) =>
            val scalaMap = value.asScala
            val profile = ProvisionerProfile(
              id,
              getStringValue(id, scalaMap, Set("label",":label")),
              getStringValue(id, scalaMap, Set("prefix", ":prefix")),
              getBooleanValue(id, scalaMap, Set("allow_suffix", ":allow_suffix"), Some(false))
            )
            Seq(profile) ++ total
          case _ => 
            total
        }
      }
    }
    for (entry <- yaml) {
      entry match {
        case (":profiles", map: java.util.Map[_, _]) => return processProfiles(map.asScala)
        case ("profiles", map: java.util.Map[_, _]) => return processProfiles(map.asScala)
        case _ =>
      }
    }
    return Seq()
  }

  private[this] def yamlFromFile(s: String) = {
    val file = new File(s)
    if (!(file.exists() && file.canRead() && file.isFile())) {
      throw InvalidConfig(Some("File %s is missing, not readable, or not a file".format(s)))
    }
    val yaml = new Yaml()
    val fis: InputStream = new FileInputStream(file)
    try {
      yaml.load(fis)
    } catch {
      case e =>
        throw InvalidConfig(Some("Error reading %s".format(s)))
    } finally {
      fis.close()
    }
  }

}
