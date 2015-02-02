package collins
package reflection

import java.lang.reflect.Method
import play.api.Logger
import util.SystemTattler

trait MethodHelper extends MethodArguments with MethodReturnType {

  val chattyFailures = false
  protected[this] val logger = Logger("MethodHelper")

  def getMethod(method: String, value: AnyRef): Option[Method] = {
    Option(value).flatMap { v =>
      try {
        Some(v.getClass().getMethod(method))
      } catch {
        case _: Throwable =>
          None
      }
    }
  }

  def invokeZeroArityMethod(method: Method, value: AnyRef): Option[AnyRef] =
    Option(value) match {
      case Some(v) =>
        if (isZeroArityMethod(method)) {
          try {
            Option(method.invoke(v)).orElse {
              logger.info("Calling %s returned null".format(method.toString))
              None
            }
          } catch {
            case e: Throwable =>
              handleFailure("Failed to invoke %s on value: %s".format(
                method.toString, e.getMessage
              ))
              None
          }
        } else {
          handleFailure("Method %s takes arguments which is not supported".format(method.toString))
          None
        }
      case None =>
        handleFailure("Can not call %s on null value".format(method.toString))
        None
    }

  def invokePredicateMethod(method: Method, value: AnyRef): Option[Boolean] =
    invokeZeroArityMethod(method, value).flatMap { value =>
      if (!isBooleanReturnType(method)) {
        handleFailure("Method %s does not return a boolean value".format(method.toString))
        None
      } else {
        Some(value.asInstanceOf[Boolean])
      }
    }

  protected def handleFailure(msg: String) {
    if (chattyFailures) {
      logger.error(msg)
      SystemTattler.safeError(msg)
    }
  }
}
