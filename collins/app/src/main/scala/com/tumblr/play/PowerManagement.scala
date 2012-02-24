package com.tumblr.play

import play.api.{Application, Configuration, Logger, PlayException, Plugin}
import com.twitter.util.Future

sealed trait PowerAction
case object PowerOff extends PowerAction {
  override def toString: String = "PowerOff"
}
case object PowerOn extends PowerAction {
  override def toString: String = "PowerOn"
}
case object PowerCycle extends PowerAction {
  override def toString: String = "PowerCycle"
}

sealed trait Reboot extends PowerAction
case object RebootSoft extends Reboot {
  override def toString: String = "RebootSoft"
}
case object RebootHard extends Reboot {
  override def toString: String = "RebootHard"
}

object Power {
  def off() = PowerOff
  def on() = PowerOn
  def cycle() = PowerCycle
  def rebootSoft() = RebootSoft
  def rebootHard() = RebootHard
  def apply(s: String): PowerAction = unapply(s) match {
    case Some(p) => p
    case None => throw new MatchError("No such power action " + s)
  }
  def unapply(t: String) = t.toLowerCase match {
    case r if RebootSoft.toString.toLowerCase == r => Some(Power.rebootSoft())
    case r if RebootHard.toString.toLowerCase == r => Some(Power.rebootHard())
    case r if PowerOff.toString.toLowerCase == r => Some(Power.off())
    case r if PowerOn.toString.toLowerCase == r => Some(Power.on())
    case r if PowerCycle.toString.toLowerCase == r => Some(Power.cycle())
    case _ => None
  }
}

trait PowerManagement extends Plugin {
  sealed trait PowerCommandStatus {
    def isSuccess: Boolean
    def description: String
  }
  case object Success extends PowerCommandStatus {
    override val isSuccess = true
    override val description = "Command successful"
  }
  case object RateLimit extends PowerCommandStatus {
    override val isSuccess = false
    override val description = "Only one power event every 20 minutes is allowed"
  }
  case class Failure(override val description: String = "Failed to execute power command") extends PowerCommandStatus {
    override val isSuccess = false
  }
  type PowerStatus = Future[PowerCommandStatus]
  def powerCycle(e: AssetWithTag): PowerStatus
  def powerOff(e: AssetWithTag): PowerStatus
  def powerOn(e: AssetWithTag): PowerStatus
  def rebootHard(e: AssetWithTag): PowerStatus
  def rebootSoft(e: AssetWithTag): PowerStatus
}
