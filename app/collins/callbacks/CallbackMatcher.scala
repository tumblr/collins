package collins
package callbacks

import java.beans.{PropertyChangeEvent, PropertyChangeListener, PropertyChangeSupport}
import java.lang.reflect.Method
import play.api.Logger

/**
 * Given a matchMethod, apply it to the PCE.
 *
 * @param matchMethod a string containing a method to apply
 * @param fn a function that takes a PCE and returns some value for it
 */
case class CallbackMatcher(matchMethod: Option[String], fn: PropertyChangeEvent => AnyRef) {

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
      try {
        val valueMethod = value.getClass().getMethod(method)
        if (!isBooleanReturnType(valueMethod)) {
          logger.error("%s does not return a boolean value".format(
            valueMethod.toString
          ))
          None
        } else if (!isZeroArityMethod(valueMethod)) {
          logger.error("%s takes arguments which is unsupported".format(
            valueMethod.toString
          ))
          None
        } else {
          Some(valueMethod.invoke(value).asInstanceOf[Boolean])
        }
      } catch {
        case e => None
      }
    }
  }

  protected def isBooleanReturnType(method: Method): Boolean =
    method.getReturnType.equals(java.lang.Boolean.TYPE)

  protected def isZeroArityMethod(method: Method): Boolean =
    method.getParameterTypes.size == 0
}
