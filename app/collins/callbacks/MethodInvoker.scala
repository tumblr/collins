package collins
package callbacks

import java.lang.reflect.Method

trait MethodInvoker {

  protected def invoke(method: Method, value: AnyRef): Option[AnyRef] = {
    try {
      Option(method.invoke(value))
    } catch {
      case e =>
        None
    }
  }

  protected def getMethod(method: String, value: AnyRef): Option[Method] = {
    Option(value).flatMap { v =>
      try {
        Some(v.getClass().getMethod(method))
      } catch {
        case e =>
          None
      }
    }
  }

  protected def isBooleanReturnType(method: Method): Boolean =
    method.getReturnType.equals(java.lang.Boolean.TYPE)

  protected def methodArity(method: Method): Int =
    method.getParameterTypes.size

  protected def isZeroArityMethod(method: Method): Boolean =
    methodArity(method) == 0
}
