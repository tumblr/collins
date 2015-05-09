package collins.callbacks

import java.beans.PropertyChangeEvent

import collins.models.State
import collins.models.asset.AssetView
import collins.reflection.MethodHelper

/**
 * Given a matchMethod, apply it to the PCE.
 *
 * @param matchMethod a string containing a method to apply
 * @param fn a function that takes a PCE and returns some value for it
 */
case class CallbackMatcher(conditional: MatchConditional, fn: PropertyChangeEvent => AnyRef)
  extends MethodHelper
{

  override val chattyFailures = true
  val name = conditional.name

  /**
   * Given a PCE, apply the matchMethod against it and return that value.
   *
   * This will only call methods that take 0 arguments and return a boolean.
   *
   * @param pce a property change event that will be passed to fn to get a value back
   * @return a boolean representing the success of the operation. True will be returned if
   * matchMethod is None. False will be returned if an error occurs.
   */
  def apply(pce: PropertyChangeEvent): Boolean = {
    checkState(pce) && checkStates(pce)
  }
  
  protected def checkState(pce: PropertyChangeEvent): Boolean = conditional.state.map { method =>
    val value = fn(pce)
    negation(method) match {
      case (true, meth) => invokeMethod(meth, value).map(b => !b).getOrElse(false)
      case (false, meth) => invokeMethod(meth, value).getOrElse(false)
    }
  }.getOrElse(true)

  protected def checkStates(pce: PropertyChangeEvent): Boolean = if (conditional.states.isEmpty) {
    true
  } else {
    val states = conditional.states.toSet
    val value = Option(fn(pce))
    value.filter(_.isInstanceOf[AssetView]).map(_.asInstanceOf[AssetView]).map { v =>
      State.findById(v.state).map { state =>
        states.map(_.toUpperCase).contains(state.name.toUpperCase)
      }.getOrElse(false)
    }.getOrElse(true)
  }

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

  protected def invokeMethod(meth: String, v: AnyRef): Option[Boolean] = {
    getMethod(meth, v).flatMap(invokePredicateMethod(_, v))
  }

}
