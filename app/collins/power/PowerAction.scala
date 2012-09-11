package collins.power

sealed trait PowerAction
case object PowerOff extends PowerAction {
  override def toString: String = "PowerOff"
}
case object PowerOn extends PowerAction {
  override def toString: String = "PowerOn"
}
case object PowerSoft extends PowerAction {
  override def toString: String = "PowerSoft"
}
case object PowerState extends PowerAction {
  override def toString: String = "PowerState"
}

sealed trait ChassisInfo extends PowerAction
case object Identify extends ChassisInfo {
  override def toString: String = "Identify"
}
case object Verify extends ChassisInfo {
  override def toString: String = "Verify"
}

sealed trait Reboot extends PowerAction
case object RebootSoft extends Reboot {
  override def toString: String = "RebootSoft"
}
case object RebootHard extends Reboot {
  override def toString: String = "RebootHard"
}

object PowerAction {
  def off() = PowerOff
  def on() = PowerOn
  def soft() = PowerSoft
  def state() = PowerState
  def rebootSoft() = RebootSoft
  def rebootHard() = RebootHard
  def verify() = Verify
  def identify() = Identify
  def apply(s: String): PowerAction = unapply(s) match {
    case Some(p) => p
    case None => throw new MatchError("No such power action " + s)
  }
  def unapply(t: String) = t.toLowerCase match {
    case r if rebootSoft().toString.toLowerCase == r => Some(rebootSoft())
    case r if rebootHard().toString.toLowerCase == r => Some(rebootHard())
    case r if off().toString.toLowerCase == r => Some(off())
    case r if on().toString.toLowerCase == r => Some(on())
    case r if soft().toString.toLowerCase == r => Some(soft())
    case r if state().toString.toLowerCase == r => Some(state())
    case r if identify().toString.toLowerCase == r => Some(identify())
    case r if verify().toString.toLowerCase == r => Some(verify())
    case _ => None
  }
}


