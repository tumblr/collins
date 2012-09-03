package collins
package callbacks

import util.SystemTattler

import play.api.Logger
import java.lang.reflect.Method

/**
 * Represents the results of a regular expression.
 *
 * This exists to capture the results of applying a regular expression against a source string. For
 * example assume the source string is: 'run_command --tag=<tag> --withStatus=<status>'
 *
 * The results of applying a regular expression could be stashed here where you might have
 * originalValue as '<tag>' and methodName as 'tag'.
 *
 * @param originalValue a string found in the original source string (e.g. <tag>)
 * @param methodName the match from the original source string (e.g. tag)
 * @param newValue the result of applying methodName on a class instance
 */
case class MethodReplacement(
  originalValue: String, methodName: String, newValue: String = ""
) extends MethodInvoker {

  protected[this] val logger = Logger("MethodReplacement")

  /**
   * Apply methodName to v, return a MethodReplacement with an updated newValue.
   *
   * @param v Any value (non-primitive)
   * @return a MethodReplacement with an updated newValue on success, or an empty newValue on
   * failure
   */
  def runMethod(v: AnyRef): MethodReplacement = {
    Option(v).flatMap { value =>
      getMethod(methodName, value)
        .orElse {
          handleError("Method %s does not exist".format(methodName))
          None
        }
        .filter(hasZeroArityMethod(_))
        .flatMap { method =>
          invoke(method, value).map(nv => this.copy(newValue = nv.toString)).orElse {
            handleError("Failed to invoke %s on value".format(method.toString))
            None
          }
        }
    }.getOrElse {
      this
    }
  }

  protected def hasZeroArityMethod(m: Method): Boolean = if (isZeroArityMethod(m)) {
    true
  } else {
    handleError(
      "Method %s takes more than zero arguments which is not supported".format(m.toString)
    )
  }

  private def handleError(msg: String): Boolean = {
    logger.error(msg)
    SystemTattler.safeError(msg)
    false
  }

}
