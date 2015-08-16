package collins.power.management

import org.specs2._
import collins.power.PowerAction
import play.api.test.WithApplication
import play.api.test.FakeApplication

class PowerManagementConfigSpec extends mutable.Specification {

  "Power Management Config" should  {
    "load the verify command" in new WithApplication {
      PowerManagementConfig.verifyCommand must_== "ping -c 3 <host>"
    }

    "load the power off command using the template" in new WithApplication {
       PowerManagementConfig.powerOffCommand must_==
        "ipmitool -H <host> -U <username> -P <password> -I lan -L OPERATOR chassis power off"
    }

    "load disallowAssetTypes correctly" in new WithApplication {
      PowerManagementConfig.disallowWhenAllocated.head must_== PowerAction("powerOff")
    }
  }
}
