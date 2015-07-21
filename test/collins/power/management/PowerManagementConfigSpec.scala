package collins.power.management

import org.specs2._
import collins.ResourceFinder
import collins.power.PowerAction
import play.api.test.FakeApplication

class PowerManagementConfigSpec extends Specification with ResourceFinder {

  def is = "PowerManagementConfig should" ^
    "load the verify command"                       ! spec().loadVerifyConfig ^
    "load the power off command using the template" ! spec().loadCommandTemplate ^
    "load disallowAssetTypes correctly"             ! spec().disallowWhenAllocated ^
  end

  // This will load the same configurations as the app
  val fakeApp = FakeApplication()

  private case class spec() {
    def loadVerifyConfig = {
      PowerManagementConfig.verifyCommand must_== "ping -c 3 <host>"
    }

    def loadCommandTemplate = {
      PowerManagementConfig.powerOffCommand must_==
        "ipmitool -H <host> -U <username> -P <password> -I lan -L OPERATOR chassis power off"
    }

    def disallowWhenAllocated = {
      PowerManagementConfig.disallowWhenAllocated.head must_== PowerAction("powerOff")
    }
  }
}
