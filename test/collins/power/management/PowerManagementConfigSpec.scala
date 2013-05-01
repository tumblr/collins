package collins.power.management

import org.specs2._
import com.typesafe.config.ConfigFactory
import collins.power.PowerAction

class PowerManagementConfigSpec extends Specification with test.ResourceFinder {

  def is = "PowerManagementConfig should" ^
    "load the verify command" ! spec().loadVerifyConfig ^
    "load disallowAssetTypes correctly" ! spec().disallowWhenAllocated ^
  end

  val file = findResource(PowerManagementConfig.referenceConfigFilename)
  val typesafeConfig =
    ConfigFactory.load(ConfigFactory.parseFileAnySyntax(file)).getConfig(PowerManagementConfig.namespace)

  PowerManagementConfig.pluginInitialize(play.api.Configuration(typesafeConfig))

  private case class spec() {
    def loadVerifyConfig = {
      PowerManagementConfig.verifyCommand must_== "ping -c 3 <host>"
    }

    def disallowWhenAllocated = {
      PowerManagementConfig.disallowWhenAllocated.head must_== PowerAction("powerOff")
    }
  }
}
