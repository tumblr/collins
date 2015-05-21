package collins.power.management

import scala.concurrent.Future

import collins.models.Asset

trait PowerManagement {
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
  def powerSoft(e: Asset): PowerStatus
  def powerOff(e: Asset): PowerStatus
  def powerOn(e: Asset): PowerStatus
  def powerState(e: Asset): PowerStatus
  def rebootHard(e: Asset): PowerStatus
  def rebootSoft(e: Asset): PowerStatus
  def verify(e: Asset): PowerStatus
  def identify(e: Asset): PowerStatus
}
