package collins.shell

/**
 * Describes the result of an executed command
 */
case class CommandResult(exitCode: Int, output: String, stderr: Option[String] = None) {
  def isSuccess: Boolean = exitCode == 0
  override def toString(): String = {
    "Exit Code: %d, Stdout: %s, Stderr: %s".format(exitCode, output, stderr.getOrElse(""))
  }
}
