package collins
package action

import play.api.Logger
import scala.collection.SeqProxy


case class ActionExecutorException(message: String) extends Exception(message)


case class FormattedValues(_stringSeq: Seq[String]) extends SeqProxy[String] {
  def self = _stringSeq
}


/**
 * Contains base methods necessary to implement a Collins Action executor, a
 * class which handles executing a Collins Action.  Type C should be the type
 * of object which stores the command to execute, such as Seq[String], while R
 * should be the type of result returned by executing the command.
 */
trait ActionExecutor {

  protected val logger = Logger("ActionExecutor") 

  val METHOD_CALL_REGEX = """\<(.*?)\>""".r

  /**
   * Stores the command to be executed as specified in the action's config,
   * typically overridden during object initialization.
   */
  val command: Seq[String] = Seq("")

  /**
   * Returns a string representation of the supplied command, separated with
   * spaces.
   *
   * @return a space-separated string representation of the supplied command.
   */
  def commandString = command.mkString(" ")

  def apply[T >: AnyRef](onObj: T): Unit = {
    throw new ActionExecutorException("apply(onObj) is undefined!")
  }

  /**
   * Executes the command specified by this Collins action, returning a String,
   * typically overridden in an ActionExecutor subclass.
   *
   * @param cmd a command of the specified type
   * @return the result from running the command as a String.
   */
  protected def runCommandString(cmd: FormattedValues): String = {
    throw new ActionExecutorException("runCommandString(cmd) is undefined!")
  }

  /**
   * Executes the command specified by this Collins action, returning a
   * Boolean, typically overridden in an ActionExecutor subclass.
   *
   * @param cmd a command of the specified type
   * @return the result from running the command as a Boolean.
   */
  protected def runCommandBoolean(cmd: FormattedValues): Boolean = {
    throw new ActionExecutorException("runCommandBoolean(cmd) is undefined!")
  }

  /**
   * Formats a command suitable for execution with the results of running a
   * method call replacement against the specified value object, typically
   * overridden in an ActionExecutor subclass.
   *
   * @param value the value which method replacement was run against
   * @param replacements the set of MethodReplacements with which to format
   * the command
   * @return a command suitable for execution.
   */
   protected def templateCommand(v: AnyRef,
      replacements: Set[MethodReplacement]): Seq[String] = {
     command.map { cmd =>
       replacements.foldLeft(cmd) { case(string, replacement) =>
          string.replaceAllLiterally(replacement.originalValue,
              replacement.newValue)
       }
     }
   }

  /**
   * Runs all user-specified method replacements against the supplied value
   * object, returning a command suitable for execution.
   *
   * @param value the value to apply method replacements against.
   * @return a command suitable for execution.
   */
  protected def getTemplatedCommand(value: AnyRef): FormattedValues = {
    val replacements = getMethodReplacements()
    logger.debug("Got replacements for command %s: %s".format(commandString,
      replacements.map(_.toString).mkString(", ")
    ))
    val replacementsWithValues = replacements.map(_.runMethod(value))
    logger.debug("Got replacements (with values) for command %s: %s".format(commandString,
      replacementsWithValues.map(_.toString).mkString(", ")
    ))
    val cmdValue = templateCommand(value, replacementsWithValues)
    logger.debug("Got new command with replacements: %s".format(cmdValue))
    FormattedValues(cmdValue)
  }

  /**
   * Obtains all desired method replacements found in the user-supplied
   * command.
   *
   * @return a Set of MethodReplacement objects suitable for running method
   * calls with.
   */
  protected def getMethodReplacements(): Set[MethodReplacement] =
    METHOD_CALL_REGEX.findAllIn(commandString).matchData.map { md =>
      MethodReplacement(md.group(0), md.group(1))
    }.toSet

}
