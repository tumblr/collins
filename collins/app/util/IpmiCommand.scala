package util

import models.{Asset, IpmiInfo}
import concurrent.BackgroundProcess
import com.tumblr.play.CommandResult

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

object IpmiCommand {
  type BackgroundResult = Tuple2[Option[Throwable], Option[Option[CommandResult]]]
  def fromResult(r: BackgroundResult): Either[Throwable,Option[CommandResult]] = r match {
    case (None, None) =>
      Left(new Exception("No command result AND no throwable"))
    case (Some(ex), _) =>
      Left(ex)
    case (None, Some(res)) =>
      Right(res)
  }
}

abstract class IpmiCommand extends BackgroundProcess[Option[CommandResult]] {
  val interval: Duration
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

  def run(): Option[CommandResult] = {
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
    val cr = CommandResult(exitStatus, stdoutString, Some(stderrString))
    if (!cr.isSuccess) {
      logger.error("Error running command '%s'".format(command))
      logger.error(cr.toString)
    } else {
      logger.info("Ran command %s".format(command))
      logger.info(cr.toString)
    }
    Some(cr)
  }

  override protected def defaultTimeout: Duration = {
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
