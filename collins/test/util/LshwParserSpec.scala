package util

import play.api.test._
import org.specs2.matcher.Matcher
import org.specs2.mutable._

class LshwParserSpec extends Specification {

  def beNonEmptyStringSeq: Matcher[Seq[String]] = have(s => s.nonEmpty && s.size >= 5)

  "The Lshw Parser" should {
   
    "Parse Dell (AMD) lshw output" in {
      "basic" in {
        val amdData = getResource("lshw-basic.xml")
        val parser = new LshwParser(amdData)
        val parsed = parser.parse()
        parsed must beRight
        val rep = parsed.right.get
  
        rep.cpuCount mustEqual 2
        rep.cpuCoreCount mustEqual 12
        rep.hasHyperthreadingEnabled must beFalse
        rep.cpuSpeed must beCloseTo(2.3, 0.1)
  
        rep.totalMemory.inGigabytes must beCloseTo(32L, 1)
        rep.memoryBanksUsed mustEqual 4
        rep.memoryBanksUnused mustEqual 8
        rep.memoryBanksTotal mustEqual 12
  
        rep.totalStorage.toHuman mustEqual "5.46 TB"
        rep.diskCount mustEqual 6
  
        rep.hasFlashStorage must beFalse
        rep.totalFlashStorage.toHuman mustEqual "0 Bytes"
        rep.totalUsableStorage.toHuman mustEqual "5.46 TB"
  
        rep.nicCount mustEqual 2
        rep.hasGbNic must beTrue
        rep.has10GbNic must beFalse
        rep.macAddresses must have length 2
        rep.macAddresses must beNonEmptyStringSeq
      }
      "with a virident card" in {
        val amdData = getResource("lshw-virident.xml")
        val parser = new LshwParser(amdData)
        val parsed = parser.parse()
        parsed must beRight
        val rep = parsed.right.get
  
        rep.cpuCount mustEqual 2
        rep.cpuCoreCount mustEqual 12
        rep.hasHyperthreadingEnabled must beFalse
        rep.cpuSpeed must beCloseTo(2.3, 0.1)
  
        rep.totalMemory.inGigabytes must beCloseTo(32L, 1)
        rep.memoryBanksUsed mustEqual 4
        rep.memoryBanksUnused mustEqual 8
        rep.memoryBanksTotal mustEqual 12
  
        rep.totalStorage.toHuman mustEqual "278.46 GB"
        rep.diskCount mustEqual 4
  
        rep.hasFlashStorage must beTrue
        rep.totalFlashStorage.toHuman mustEqual "1.27 TB"
        rep.totalUsableStorage.toHuman mustEqual "1.55 TB"
  
        rep.nicCount mustEqual 2
        rep.hasGbNic must beTrue
        rep.has10GbNic must beFalse
        rep.macAddresses must have length 2
        rep.macAddresses must beNonEmptyStringSeq
      }
      "with a 10-Gig card" in {
        val tenGData = getResource("lshw-10g.xml")
        val parser = new LshwParser(tenGData)
        val parsed = parser.parse()
        parsed must beRight
        val rep = parsed.right.get
  
        rep.cpuCount mustEqual 2
        rep.cpuCoreCount mustEqual 12
        rep.hasHyperthreadingEnabled must beFalse
        rep.cpuSpeed must beCloseTo(2.3, 0.1)
  
        rep.totalMemory.inGigabytes must beCloseTo(32L, 1)
        rep.memoryBanksUsed mustEqual 4
        rep.memoryBanksUnused mustEqual 8
        rep.memoryBanksTotal mustEqual 12
  
        rep.totalStorage.toHuman mustEqual "5.46 TB"
        rep.diskCount mustEqual 6
  
        rep.nicCount mustEqual 3
        rep.hasGbNic must beTrue
        rep.has10GbNic must beTrue
        rep.macAddresses must have length 3
        rep.macAddresses must beNonEmptyStringSeq
      }
    } // Parse dell (AMD) lshw output


    "Leverage config for flash disks" in {
      val amdData = getResource("lshw-virident.xml")
      "Different flash description and size" in {
        val config = Map(
          "flashDescription" -> "flash memory",
          "flashSize" -> "1048576"
        )
        val parser = new LshwParser(amdData, config)
        val parsed = parser.parse()
        parsed must beRight
        val rep = parsed.right.get
        rep.hasFlashStorage must beTrue
        rep.totalFlashStorage.toHuman mustEqual "1.00 MB"
        rep.totalUsableStorage.toHuman mustEqual "278.47 GB"
      }
      "Bad flash description" in {
        val config = Map(
          "flashDescription" -> "flashing memory",
          "flashSize" -> "1048576"
        )
        val parser = new LshwParser(amdData, config)
        val parsed = parser.parse()
        parsed must beRight
        val rep = parsed.right.get
        rep.hasFlashStorage must beFalse
        rep.totalFlashStorage.toHuman mustEqual "0 Bytes"
        rep.totalUsableStorage.toHuman mustEqual "278.46 GB"
      }
    }

    "Parse softlayer supermicro (Intel) lshw output" in {
      "B.02.15 format" in {
        val intelData = getResource("lshw-intel.xml")
        val parser = new LshwParser(intelData)
        val parsed = parser.parse()
        parsed must beRight
        val rep = parsed.right.get
  
        rep.cpuCount mustEqual 2
        rep.cpuCoreCount mustEqual 12
        rep.hasHyperthreadingEnabled must beTrue
        rep.cpuSpeed must beCloseTo(1.6, 0.1)
  
        rep.totalMemory.inGigabytes must beCloseTo(72L, 1)
        rep.memoryBanksUsed mustEqual 18
        rep.memoryBanksUnused mustEqual 0
        rep.memoryBanksTotal mustEqual 18
  
        rep.totalStorage.toHuman mustEqual "930.99 GB"
        rep.diskCount mustEqual 3
  
        rep.nicCount mustEqual 4
        rep.hasGbNic must beTrue
        rep.has10GbNic must beFalse
        rep.macAddresses must have length 4
        rep.macAddresses must beNonEmptyStringSeq
      }

      "B.02.12 format" in {
        val oldData = getResource("lshw-old.xml")
        val parser = new LshwParser(oldData)
        val parsed = parser.parse()
        parsed must beRight
        val rep = parsed.right.get
  
        rep.cpuCount mustEqual 11
        rep.cpuCoreCount mustEqual 11
        rep.hasHyperthreadingEnabled must beFalse
        rep.cpuSpeed must beCloseTo(1.6, 0.1)
  
        rep.totalMemory.inGigabytes must beCloseTo(72L, 1)
        rep.memoryBanksUsed mustEqual 18
        rep.memoryBanksUnused mustEqual 0
        rep.memoryBanksTotal mustEqual 18
  
        rep.totalStorage.toHuman mustEqual "930.99 GB"
        rep.diskCount mustEqual 3
  
        rep.nicCount mustEqual 4
        rep.hasGbNic must beTrue
        rep.has10GbNic must beFalse
        rep.macAddresses must have length 4
        rep.macAddresses must beNonEmptyStringSeq
      }
    } // Parse softlayer supermicro (Intel) lshw output"

  } // The LSHW parser should

  def getResource(filename: String) = {
    val stream = getClass.getClassLoader.getResourceAsStream(filename)
    val tmp = io.Source.fromInputStream(stream)
    val str = tmp.mkString
    tmp.close()
    str
  }

}
