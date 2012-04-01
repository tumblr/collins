package util

import models.{Asset, IpmiInfo}

import akka.actor.Actor
import akka.actor.Actor._
import akka.dispatch.FutureTimeoutException
import akka.routing.Routing.loadBalancerActor
import akka.routing.CyclicIterator
import akka.util.Duration
import akka.util.duration._
import play.api.{Logger, Mode}
import play.api.libs.akka._
import play.api.libs.concurrent.{Redeemed, Thrown}

import scala.collection.mutable.StringBuilder
import scala.sys.process._

class IpmiCommandProcessor extends Actor {
  def receive = {
    case cmd: IpmiCommand => self.reply(cmd.run())
  }
}

case class IpmiCommandResult(exitCode: Int, stdout: String, stderr: String) {
  override def toString(): String = {
    val ec = "Exit Code: %d".format(exitCode)
    val ss = if (stdout.isEmpty) "" else "Stdout: %s".format(stdout.replaceAll("\n","_"))
    val se = if (stderr.isEmpty) "" else "Stderr: %s".format(stderr.replaceAll("\n","_"))
    List(ec, ss, se).filter(_.nonEmpty).mkString(", ")
  }
  def isSuccess: Boolean = exitCode == 0
}
object IpmiCommandResult {
  def apply(error: String) = new IpmiCommandResult(-1, "", error.trim)
}

object IpmiCommandProcessor {
  val ref = loadBalancerActor(
    new CyclicIterator((1 to ActorConfig.ActorCount)
      .map(_ => actorOf[IpmiCommandProcessor].start())
      .toList
    )
  )
  def send[A](cmd: IpmiCommand)(result: Option[IpmiCommandResult] => A) = {
    ref.?(cmd)(timeout = cmd.timeout).mapTo[Option[IpmiCommandResult]].asPromise.extend1 {
      case Redeemed(v) => result(v)
      case Thrown(e) => e match {
        case t: FutureTimeoutException =>
          result(Option(IpmiCommandResult(
            "Command took longer than %d seconds: %s".format(cmd.timeout.toSeconds, t.getMessage)
          )))
        case _ =>
          result(Option(IpmiCommandResult(e.getMessage)))
      }
    }
  }
}

abstract class IpmiCommand {
  val interval: Duration
  val timeout: Duration
  val configKey: String
  var debug: Boolean = false

  protected def ipmiInfo: IpmiInfo

  protected val logger = Logger(getClass)

  protected lazy val (address, username, password) = {
    val ipmi = ipmiInfo
    (ipmi.dottedAddress(), ipmi.username, ipmi.decryptedPassword())
  }

  def shouldRun(): Boolean = {
    AppConfig.isProd() || debug
  }

  def getConfig(): Map[String,String] = {
    AppConfig.ipmiMap
  }

  def run(): Option[IpmiCommandResult] = {
    if (!shouldRun) {
      return None
    }
    val command = substitute(getIpmiCommand())
    val process = Process(command, None, ("IPMI_PASSWORD" -> password))
    val stdout = new StringBuilder()
    val stderr = new StringBuilder()
    val exitStatus = try {
      process ! ProcessLogger(
        s => stdout.append(s + "\n"),
        e => stderr.append(e + "\n")
      )
    } catch {
      case e: Throwable =>
        stderr.append(e.getMessage)
        -1
    }
    val stdoutString = stdout.toString.trim
    val stderrString = stderr.toString.trim
    val icr = IpmiCommandResult(exitStatus, stdoutString, stderrString)
    if (!icr.isSuccess) {
      logger.error("Error running command '%s'".format(command))
      logger.error(icr.toString)
    } else {
      logger.info("Ran command %s".format(command))
      logger.info(icr.toString)
    }
    Some(icr)
  }

  protected def defaultTimeout: Duration = {
    Duration.parse(getConfig.getOrElse("timeout", "2 seconds"))
  }

  protected def getIpmiCommand(): String = {
    val config = getConfig
    if (config.isEmpty)
      throw new IllegalStateException("No valid ipmi configuration available")
    val ipmiCmd = config.get(configKey)
    if (!ipmiCmd.isDefined)
      throw new IllegalStateException("No %s configuration available".format(configKey))
    ipmiCmd.get
  }

  protected def substitute(cmd: String): String = {
    cmd.replace("<host>", address)
      .replace("<username>", username)
      .replace("<password>", password)
      .replace("<interval>", interval.toSeconds.toString)
  }
}

case class IpmiIdentifyCommand(asset: Asset, interval: Duration, userTimeout: Option[Duration] = None)
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
