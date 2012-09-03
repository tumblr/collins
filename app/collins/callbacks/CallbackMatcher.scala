package collins
package callbacks

import util.SystemTattler

import java.beans.{PropertyChangeEvent, PropertyChangeListener, PropertyChangeSupport}
import java.lang.reflect.Method
import play.api.Logger

/**
 * Given a matchMethod, apply it to the PCE.
 *
 * @param matchMethod a string containing a method to apply
 * @param fn a function that takes a PCE and returns some value for it
 */
case class CallbackMatcher(matchMethod: Option[String], fn: PropertyChangeEvent => AnyRef)
  extends MethodInvoker
{

  protected[this] val logger = Logger("CallbackMatcher")

  /**
   * Given a PCE, apply the matchMethod against it and return that value.
   *
   * This will only call methods that take 0 arguments and return a boolean.
   *
   * @param pce a property change event that will be passed to fn to get a value back
   * @return a boolean representing the success of the operation. True will be returned if
   * matchMethod is None. False will be returned if an error occurs.
   */
  def apply(pce: PropertyChangeEvent): Boolean = matchMethod.map { method =>
    val value = fn(pce)
    negation(method) match {
      case (true, meth) => invoke(meth, value).map(b => !b).getOrElse(false)
      case (false, meth) => invoke(meth, value).getOrElse(false)
    }
  }.getOrElse(true)

  /**
   * Given a method name, determine if it describes a negating function or not
   *
   * Examples:
   *   negation("someMethod")  -> (false, "someMethod")
   *   negation("!someMethod") -> (true, "someMethod")
   *
   * @param method the method being executed
   * @return a tuple where the left is true if this is a negation, and the right is the method name
   */
  protected def negation(method: String): Tuple2[Boolean,String] = method.startsWith("!") match {
    case true => (true, method.drop(1))
    case false => (false, method)
  }

  protected def invoke(method: String, value: AnyRef): Option[Boolean] = {
    Option(value).flatMap { v =>
      getMethod(method, v)
        .orElse {
          handleError("Method %s does not exist".format(method))
          None
        }
        .filter(hasBooleanReturnType(_))
        .filter(hasZeroArityMethod(_))
        .flatMap { method =>
          invoke(method, v).map(_.asInstanceOf[Boolean]).orElse {
            handleError("Failed to invoke %s on value".format(method.toString))
            None
          }
        }
    }
  }

  protected def hasBooleanReturnType(m: Method): Boolean = if (isBooleanReturnType(m)) {
    true
  } else {
    handleError(
      "Method %s does not have a boolean return value".format(m.toString)
    )
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
