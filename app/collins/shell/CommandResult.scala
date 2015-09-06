package collins.shell

/**
 * Describes the result of an executed command
 */
case class CommandResult(exitCode: Int, stdout: String, stderr: Option[String] = None) {
  def isSuccess: Boolean = exitCode == 0
  override def toString(): String = {
    stderr match {
      case None =>
        "Exit Code: %d, Stdout: %s".format(exitCode, stdout)
      case Some(err) =>
        "Exit Code: %d, Stdout: %s, Stderr: %s".format(
          exitCode, stdout, err)
    }
  }
}
