package collins
package action

import play.api.Logger
import scala.collection.mutable.ListBuffer
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

  protected val logger = Logger(getClass) 

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

  /**
   * Formats the value contained in a string by executing the specified Collins
   * Action, returning the String as formatted by the Action.
   *
   * @param value the String to format.
   * @return the formatted String.
   */
  def formatValue(value: String): String = {
    runCommandString(buildFormatCommand(value))
  }

  /**
   * Executes the command specified by this Collins action, returning an AnyRef,
   * typically overridden in an ActionExecutor subclass.
   *
   * @param cmd a command to be executed.
   * @return the raw result from executing the command.
   */
  protected def runCommandGeneric(cmd: AnyRef*): Option[AnyRef]

  /**
   * Executes the command specified by this Collins action, returning a String,
   * typically overridden in an ActionExecutor subclass.
   *
   * @param cmd a command to be executed.
   * @return the result from executing the command, formatted as a String.
   */
  protected def runCommandString(cmd: FormattedValues): String

  /**
   * Executes the command specified by this Collins action, returning a
   * Boolean, typically overridden in an ActionExecutor subclass.
   *
   * @param cmd a command to be executed.
   * @return the result from executing the command, formatted as a Boolean.
   */
  protected def runCommandBoolean(cmd: FormattedValues): Boolean

  /**
   * Builds a command suitable for FormattedValues execution f,
   * beginning with the first element of the command, followed by the object
   * upon which the Action will be predicated, followed by any other
   * user-specified arguments as Strings.
   *
   * @param withObj the Collins object upon which the Collins Action will be
   * predicated.
   * @return a Sequence of values suitable for execution as a Collins Action.
   */
  protected def buildFormatCommand(withString: String): FormattedValues = {
    var commandWithString = new ListBuffer[String]()
    commandWithString += command(0)
    commandWithString += withString
    command.slice(1, command.length).foreach{ cmdPart =>
      commandWithString += cmdPart
    }
    FormattedValues(commandWithString)
  }

  /**
   * Obtains all desired method replacements found in the user-supplied
   * command, 
   *
   * @return a Set of MethodReplacement objects suitable for running method
   * calls with.
   */
  protected def getMethodReplacements(): Set[MethodReplacement] =
    METHOD_CALL_REGEX.findAllIn(commandString).matchData.map { md =>
      MethodReplacement(md.group(0), md.group(1))
    }.toSet

  /**
   * Templates a command suitable for sending to a generic Collins Action,
   * beginning with the first element of the command, followed by the object
   * upon which the Action will be predicated, followed by any other
   * user-specified arguments as Strings, templated against the supplied
   * object.
   *
   * @param withObj the Collins object upon which the Action will be
   * predicated.
   * @return a Sequence of values suitable for passing to a generic Collins
   * Action.
   */
  protected def templateCommandWithObject(withObj: AnyRef): Seq[AnyRef] = {
    val templatedCommand = templateFormattedValues(withObj)
    var commandWithValue = new ListBuffer[AnyRef]()
    commandWithValue += templatedCommand(0)
    commandWithValue += withObj
    templatedCommand.slice(1, command.length).foreach{ cmdPart =>
      commandWithValue += cmdPart
    }
    commandWithValue
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
  protected def templateFormattedValues(value: AnyRef): FormattedValues = {
    val replacements = getMethodReplacements()
    logger.debug("Got replacements for command %s: %s".format(commandString,
      replacements.map(_.toString).mkString(", ")
    ))
    val replacementsWithValues = replacements.map(_.runMethod(value))
    logger.debug("Got replacements (with values) for command %s: %s".format(
        commandString, replacementsWithValues.map(_.toString).mkString(", ")
    ))
    val cmdValue = command.map { cmd =>
      replacementsWithValues.foldLeft(cmd) { case(string, replacement) =>
         string.replaceAllLiterally(replacement.originalValue,
             replacement.newValue)
      }
    }
    logger.debug("Got new command with replacements: %s".format(cmdValue))
    FormattedValues(cmdValue)
  }

}
