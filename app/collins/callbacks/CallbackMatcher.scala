package collins.callbacks

import java.beans.PropertyChangeEvent

import collins.models.State
import collins.models.Asset
import collins.models.AssetMetaValue
import collins.models.IpAddresses

/**
 * Given a matchMethod, apply it to the PCE.
 *
 * @param matchMethod a string containing a method to apply
 * @param fn a function that takes a PCE and returns some value for it
 */
case class CallbackMatcher(conditional: MatchConditional, fn: PropertyChangeEvent => CallbackDatumHolder)
    extends MethodHelper {

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
    val asset = fn(pce) match {
      case CallbackDatumHolder(Some(a: Asset)) => Some(a)
      case CallbackDatumHolder(Some(v: AssetMetaValue)) => Some(v.asset)
      case CallbackDatumHolder(Some(i: IpAddresses)) => Some(i.getAsset)
      case _ => None
    }

    checkState(pce, asset) && checkStates(pce, asset)
  }

  protected def checkState(pce: PropertyChangeEvent, asset: Option[Asset]): Boolean = conditional.state.map { method =>
    negation(method) match {
      case (true, meth)  => invokeMethod(meth, asset).map(b => !b).getOrElse(false)
      case (false, meth) => invokeMethod(meth, asset).getOrElse(false)
    }
  }.getOrElse(true)

  protected def checkStates(pce: PropertyChangeEvent, asset: Option[Asset]): Boolean = if (conditional.states.isEmpty) {
    true
  } else {
    val states = conditional.states.toSet
    asset match {
      case Some(v: Asset) => {
        State.findById(v.stateId).map { state =>
          states.map(_.toUpperCase).contains(state.name.toUpperCase)
        }.getOrElse(false)
      }
      case _ => true
    }
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
  private[this] def negation(method: String): Tuple2[Boolean, String] = method.startsWith("!") match {
    case true  => (true, method.drop(1))
    case false => (false, method)
  }

  private[this] def invokeMethod(meth: String, assetOpt: Option[Asset]): Option[Boolean] = {
    for {
      asset <- assetOpt
      method <- getMethod(meth, asset)
      pv <- invokePredicateMethod(method, asset)
    } yield pv
  }

}
