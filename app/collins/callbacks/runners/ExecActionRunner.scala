package collins
package callbacks
package runners

import scala.collection.mutable.StringBuilder
import scala.sys.process._

case class ExecActionRunner(command: Seq[String]) extends CallbackActionRunner[Seq[String]] {

  override def formatCommand(v: AnyRef, replacements: Set[MethodReplacement]): Seq[String] = {
    command.map { cmd =>
      replacements.foldLeft(cmd) { case(string, replacement) =>
        string.replaceAllLiterally(replacement.originalValue, replacement.newValue)
      }
    }
  }

  override protected def runCommand(cmd: Seq[String]) {
    val process = if (cmd.size == 1) {
      // If we got a string which was converted to a list, use the normal Process parsing
      Process(cmd.head)
    } else {
      Process(cmd)
    }
    val stdout = new StringBuilder()
    val stderr = new StringBuilder()
    val exitStatus = try {
      process ! ProcessLogger(
        s => stdout.append(s + "\n"),
        e => stderr.append(e + "\n")
      )
    } catch {
      case e =>
        stderr.append(e.getMessage)
        -1
    }
    logger.info("Ran command %s".format(cmd))
    logger.info("Exit status was %d".format(exitStatus))
    logger.info("Stdout: %s".format(stdout.toString))
    logger.info("Stderr: %s".format(stderr.toString))
  }

}
