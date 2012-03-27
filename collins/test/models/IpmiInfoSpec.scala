package models

import test.ApplicationSpecification
import util.IpAddress

import play.api.Configuration

import org.specs2._
import specification._

class IpmiInfoSpec extends ApplicationSpecification {
  
  "IpmiInfo Model Specification".title

  args(sequential = true)

  def ipmiAsset(tag: String = "tumblrtag1") = Asset.findByTag(tag).get
  def newIpmiAsset(tag: String) = {
    val asset = Asset(tag, Status.Enum.Incomplete, AssetType.Enum.ServerNode)
    try {
      Asset.create(asset)
    } catch {
      case e =>
        println("Caught creating asset with tag %s".format(tag))
        throw e
    }
  }

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
      "nextAvailableAddress" in {
        val startAt = Some("172.16.32.20")
        val l = IpmiInfo.getNextAvailableAddress(startAt)._2
        val s = IpAddress.toString(l)
        s mustEqual "172.16.32.20"
      }
      "nextAvailableAddress, rollover" in {
        val startAt = Some("172.16.33.1")
        val l = IpmiInfo.getNextAvailableAddress(startAt)._2
        val s = IpAddress.toString(l)
        s mustEqual "172.16.33.1"
      }
      "createForAsset is next address" in {
        val a1 = newIpmiAsset("ipmiAssetTag1")
        val a2 = newIpmiAsset("ipmiAssetTag2")
        val a3 = newIpmiAsset("ipmiAssetTag3")
        IpmiInfo.createForAsset(a1).dottedAddress mustEqual "172.16.32.20"
        IpmiInfo.createForAsset(a2).dottedAddress mustEqual "172.16.32.21"
        IpmiInfo.createForAsset(a3).dottedAddress mustEqual "172.16.32.22"
        IpmiInfo.deleteByAsset(a3) mustEqual 1
        IpmiInfo.createForAsset(a3).dottedAddress mustEqual "172.16.32.22"
      }
      "createForAsset with rollover" in {
        val asset = ipmiAsset("ipmiAssetTag3")
        val ipmiInfo = IpmiInfo.findByAsset(asset).get
        IpmiInfo.update(ipmiInfo.copy(address = IpAddress.toLong("172.16.32.254"))) mustEqual 1
        val a4 = newIpmiAsset("ipmiAssetTag4")
        val ipmi4 = IpmiInfo.createForAsset(a4)
        ipmi4.dottedAddress mustEqual "172.16.33.1"
        ipmi4.dottedGateway mustEqual "172.16.32.1"
      }
      "findByAsset" in {
        IpmiInfo.findByAsset(ipmiAsset()) must beSome[IpmiInfo]
      }
    }

    "Use configured username options" in {
      "when username is set" in {
        val config = Configuration.from(Map(
          "username"       -> "root",
          "randomUsername" -> "false"
        ))
        val ipmiUsername = new IpmiInfo.Username(ipmiAsset(), config, false)
        ipmiUsername.get mustEqual "root"
      }
      "when randomUsername is set, override passed in config values" in {
        val config = Configuration.from(Map(
          "randomUsername" -> "true"
        ))
        val ipmiUsername = new IpmiInfo.Username(ipmiAsset(), config, false)
        ipmiUsername.get mustNotEqual ipmiUsername.get
      }
      "when randomUsername is unset, respect passed in config values" in {
        val ipmiUsername = new IpmiInfo.Username(ipmiAsset(), None, false)
        ipmiUsername.get mustEqual ipmiUsername.get

        val ipmiUsername2 = new IpmiInfo.Username(ipmiAsset(), None, true)
        ipmiUsername2.get mustNotEqual ipmiUsername2.get
      }
    }

  }

}
