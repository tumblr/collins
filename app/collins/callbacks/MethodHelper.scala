package collins.callbacks

import java.lang.reflect.Method

import play.api.Logger

import collins.util.InternalTattler

trait MethodHelper extends MethodArguments with MethodReturnType {

  val chattyFailures = false
  protected[this] val logger = Logger("MethodHelper")

  def getMethod(method: String, value: CallbackDatum): Option[Method] = {
    try {
      Some(value.getClass().getMethod(method))
    } catch {
      case _: Throwable =>
        None
    }
  }

  def invokeZeroArityMethod(method: Method, value: CallbackDatum): Option[AnyRef] =
    if (isZeroArityMethod(method)) {
      try {
        Option(method.invoke(value)).orElse {
          logger.info("Calling %s returned null".format(method.toString))
          None
        }
      } catch {
        case e: Throwable =>
          handleFailure("Failed to invoke %s on value: %s".format(
            method.toString, e.getMessage))
          None
      }
    } else {
      handleFailure("Method %s takes arguments which is not supported".format(method.toString))
      None
    }

  def invokePredicateMethod(method: Method, value: CallbackDatum): Option[Boolean] =
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
      InternalTattler.system(msg)
    }
  }
}
