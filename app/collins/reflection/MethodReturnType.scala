package collins.reflection

import java.lang.reflect.Method

trait MethodReturnType {
  def isBooleanReturnType(method: Method): Boolean =
    method.getReturnType.equals(java.lang.Boolean.TYPE)
}
