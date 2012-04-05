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
case object PowerSoft extends PowerAction {
  override def toString: String = "PowerSoft"
}
case object PowerState extends PowerAction {
  override def toString: String = "PowerState"
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
  def soft() = PowerSoft
  def state() = PowerState
  def rebootSoft() = RebootSoft
  def rebootHard() = RebootHard
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
    case _ => None
  }
}

trait PowerManagement extends Plugin {
  sealed trait PowerCommandStatus {
    def isSuccess: Boolean
    def description: String
  }
  case class Success(override val description: String = "Command successful") extends PowerCommandStatus {
    override val isSuccess = true
  }
  case object RateLimit extends PowerCommandStatus {
    override val isSuccess = false
    override val description = "Only one power event every 20 minutes is allowed"
  }
  case class Failure(override val description: String = "Failed to execute power command") extends PowerCommandStatus {
    override val isSuccess = false
  }
  type PowerStatus = Future[PowerCommandStatus]
  def powerSoft(e: AssetWithTag): PowerStatus
  def powerOff(e: AssetWithTag): PowerStatus
  def powerOn(e: AssetWithTag): PowerStatus
  def powerState(e: AssetWithTag): PowerStatus
  def rebootHard(e: AssetWithTag): PowerStatus
  def rebootSoft(e: AssetWithTag): PowerStatus
}
