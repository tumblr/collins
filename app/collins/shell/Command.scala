package collins.shell

import play.api.Logger
import scala.collection.mutable.StringBuilder
import scala.sys.process._

case class Command(command: Seq[String], logger: Logger) {
  def run(): CommandResult = {
    val process = if (command.size == 1) {
      // If we got a string which was converted to a list, use the normal Process parsing
      Process(command.head)
    } else {
      Process(command)
    }
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
    val stderrStr = stderr.toString
    val stderrOpt = if (stderrStr.isEmpty) None else Some(stderrStr)
    val result = CommandResult(exitStatus, stdout.toString, stderrOpt)
    logger.info(result.toString)
    result
  }
}
object Command {
  def apply(command: String): Command = new Command(Seq(command), Logger("util.shell.Command"))
  def apply(command: Seq[String]): Command = new Command(command, Logger("util.shell.Command"))
}
