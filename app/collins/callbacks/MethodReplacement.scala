package collins.callbacks

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
    originalValue: String, methodName: String, newValue: String = "") extends MethodHelper {

  override val chattyFailures = true

  /**
   * Apply methodName to v, return a MethodReplacement with an updated newValue.
   *
   * @param v Any value (non-primitive)
   * @return a MethodReplacement with an updated newValue on success, or an empty newValue on
   * failure
   */
  def runMethod(v: CallbackDatum): MethodReplacement = {
    getMethod(methodName, v).flatMap { method =>
      invokeZeroArityMethod(method, v)
        .map(_.toString)
        .map(s => this.copy(newValue = s))
    }.getOrElse(this)
  }

}
