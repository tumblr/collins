package collins
package callbacks
package runners

import scala.collection.mutable.StringBuilder
import scala.sys.process._

case class ExecActionRunner(command: String) extends CallbackActionRunner[String] {

  override def formatCommand(v: AnyRef, replacements: Set[MethodReplacement]): String = {
    replacements.foldLeft(command) { case(string, replacement) =>
      string.replaceAllLiterally(replacement.originalValue, replacement.newValue)
    }
  }

  override protected def runCommand(cmd: String) {
    val process = Process(cmd)
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
