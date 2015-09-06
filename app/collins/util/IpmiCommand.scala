package collins.util

import scala.collection.mutable.StringBuilder
import scala.concurrent.duration.Duration
import scala.sys.process.Process
import scala.sys.process.ProcessLogger

import play.api.Logger

import collins.models.IpmiInfo
import collins.shell.CommandResult
import collins.util.concurrent.BackgroundProcess
import collins.util.config.AppConfig

abstract class IpmiCommand extends BackgroundProcess[Option[CommandResult]] {
  val interval: Duration
  var debug: Boolean = false

  protected def ipmiInfo: IpmiInfo
  protected def ipmiCommand: String

  protected val logger = Logger(getClass)

  protected lazy val (address, username, password) = {
    val ipmi = ipmiInfo
    (ipmi.dottedAddress(), ipmi.username, ipmi.decryptedPassword())
  }

  def shouldRun(): Boolean = {
    AppConfig.isProd() || debug
  }

  def run(): Option[CommandResult] = {
    if (!shouldRun) {
      return None
    }
    val command = substitute(ipmiCommand)
    val process = Process(command, None, ("IPMI_PASSWORD" -> password))
    val stdout = new StringBuilder()
    val stderr = new StringBuilder()
    val exitStatus = try {
      process ! ProcessLogger(
        s => stdout.append(s + "\n"),
        e => stderr.append(e + "\n"))
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

  protected def substitute(cmd: String): String = {
    cmd.replace("<host>", address)
      .replace("<username>", username)
      .replace("<password>", password)
      .replace("<interval>", interval.toSeconds.toString)
  }
}
