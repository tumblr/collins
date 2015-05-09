package collins.callbacks.runners

import collins.callbacks.CallbackActionRunner
import collins.callbacks.MethodReplacement
import collins.shell.Command

case class ExecActionRunner(command: Seq[String]) extends CallbackActionRunner[Seq[String]] {

  override def formatCommand(v: AnyRef, replacements: Set[MethodReplacement]): Seq[String] = {
    command.map { cmd =>
      replacements.foldLeft(cmd) { case(string, replacement) =>
        string.replaceAllLiterally(replacement.originalValue, replacement.newValue)
      }
    }
  }

  override protected def runCommand(cmd: Seq[String]) {
    Command(cmd, logger).run()
  }

}
