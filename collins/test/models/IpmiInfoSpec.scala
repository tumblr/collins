package models

import test.ApplicationSpecification
import util.IpAddress

import play.api.Configuration

import org.specs2._
import specification._

class IpmiInfoSpec extends ApplicationSpecification {
  
  "IpmiInfo Model Specification".title

  args(sequential = true)

  def ipmiAsset = Asset.findByTag("tumblrtag1").get

  "The IpmiInfo Model" should {

    "Handle validation" in {
      "Disallow negative values" in {
        IpmiInfo(1,"foo","bar",0,1,1).validate() must throwA[IllegalArgumentException]
        IpmiInfo(1,"foo","bar",1,0,1).validate() must throwA[IllegalArgumentException]
        IpmiInfo(1,"foo","bar",1,1,0).validate() must throwA[IllegalArgumentException]
      }
      "Disallow empty username/passwords" in {
        IpmiInfo(1,"","bar",1,1,1).validate() must throwA[IllegalArgumentException]
        IpmiInfo(1,"foo","",1,1,1).validate() must throwA[IllegalArgumentException]
      }
    }

    "Support find methods" in {
      "findByAsset" in {
        IpmiInfo.findByAsset(ipmiAsset) must beSome[IpmiInfo]
      }
      "nextAvailableAddress" in {
        val netmask = IpAddress.toLong("255.255.240.0")
        val gateway = IpAddress.toLong("172.16.32.1")
        val l = IpmiInfo.getNextAvailableAddress(gateway, netmask)
        val s = IpAddress.toString(l)
        s mustEqual "172.16.32.2"
      }
    }

    "Use configured username options" in {
      "when username is set" in {
        val config = Configuration.from(Map(
          "username"       -> "root",
          "randomUsername" -> "false"
        ))
        val ipmiUsername = new IpmiInfo.Username(ipmiAsset, config, false)
        ipmiUsername.get mustEqual "root"
      }
      "when randomUsername is set, override passed in config values" in {
        val config = Configuration.from(Map(
          "randomUsername" -> "true"
        ))
        val ipmiUsername = new IpmiInfo.Username(ipmiAsset, config, false)
        ipmiUsername.get mustNotEqual ipmiUsername.get
      }
      "when randomUsername is unset, respect passed in config values" in {
        val ipmiUsername = new IpmiInfo.Username(ipmiAsset, None, false)
        ipmiUsername.get mustEqual ipmiUsername.get

        val ipmiUsername2 = new IpmiInfo.Username(ipmiAsset, None, true)
        ipmiUsername2.get mustNotEqual ipmiUsername2.get
      }
    }

  }

}
