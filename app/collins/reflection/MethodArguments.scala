package collins.reflection

import java.lang.reflect.Method

trait MethodArguments {
  def methodArity(method: Method): Int =
    method.getParameterTypes.size

  def isZeroArityMethod(method: Method): Boolean =
    methodArity(method) == 0
}
