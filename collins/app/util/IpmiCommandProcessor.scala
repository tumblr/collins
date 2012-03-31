package util

import models.{Asset, IpmiInfo}

import akka.actor.Actor
import Actor._
import akka.dispatch.FutureTimeoutException
import akka.util.Duration
import akka.util.duration._
import play.api.Mode
import play.api.libs.akka._
import play.api.libs.concurrent.{Redeemed, Thrown}

import scala.collection.mutable.StringBuilder
import scala.sys.process._

class IpmiCommandProcessor extends Actor {
  def receive = {
    case cmd: IpmiCommand => self.reply(cmd.run())
  }
}

object IpmiCommandProcessor {
  lazy val ref = actorOf[IpmiCommandProcessor].start()

  def send[A](cmd: IpmiCommand)(result: Option[String] => A) = {
    ref.?(cmd)(timeout = cmd.timeout).mapTo[Option[String]].asPromise.extend1 {
      case Redeemed(v) => result(v)
      case Thrown(e) => e match {
        case t: FutureTimeoutException =>
          result(Option("Command took longer than %d seconds: %s".format(cmd.timeout.toSeconds, t.getMessage)))
        case _ =>
          result(Option(e.getMessage))
      }
    }
  }
}

abstract class IpmiCommand {
  val duration: Duration
  val timeout: Duration
  val configKey: String
  var debug: Boolean = false

  protected def ipmiInfo: IpmiInfo

  protected lazy val (address, username, password) = {
    val ipmi = ipmiInfo
    (ipmi.dottedAddress(), ipmi.username, ipmi.decryptedPassword())
  }

  def run(): Option[String] = {
    if (!AppConfig.isProd() && !debug) {
      return None
    }
    val cmd = substitute(getIpmiCommand())
    val process = Process(cmd, None, ("IPMI_PASSWORD" -> password))
    val sb = new StringBuilder()
    val exitStatus = try {
      process ! ProcessLogger({s => sb.append(s)})
    } catch {
      case e: Throwable =>
        sb.append(e.getMessage)
        -1
    }
    exitStatus match {
      case 0 => None
      case n => Some(sb.toString)
    }
  }

  protected def defaultTimeout: Duration = {
    val config = AppConfig.ipmiMap
    Duration.parse(config.getOrElse("timeout", "2 seconds"))
  }

  protected def getIpmiCommand(): String = {
    val config = AppConfig.ipmiMap
    if (config.isEmpty)
      throw new IllegalStateException("No ipmi configuration available")
    val identifyCmd = config.get(configKey)
    if (!identifyCmd.isDefined)
      throw new IllegalStateException("No ipmi.%s configuration available".format(configKey))
    identifyCmd.get
  }

  protected def substitute(cmd: String): String = {
    cmd.replace("<host>", address)
      .replace("<username>", username)
      .replace("<password>", password)
      .replace("<interval>", duration.toSeconds.toString)
  }
}

case class IpmiIdentifyCommand(asset: Asset, duration: Duration, userTimeout: Option[Duration] = None)
  extends IpmiCommand
{
  val configKey = "identify"
  val timeout = userTimeout.getOrElse(defaultTimeout)

  override protected def ipmiInfo: IpmiInfo = {
    val _ipmi = IpmiInfo.findByAsset(asset)
    if (!_ipmi.isDefined) {
      throw new IllegalStateException("Could not find IPMI info for asset")
    }
    _ipmi.get
  }
}


