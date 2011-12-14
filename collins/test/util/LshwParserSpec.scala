package util

import org.specs2.mutable._

class LshwParserSpec extends Specification {

  "The Lshw Parser" should {
    
    "Parse standard dell (AMD) lshw output" in {
      val amdData = getResource("lshw-basic.xml")
      val parser = new LshwParser(amdData)
      val parsed = parser.parse()
      parsed must beRight
      val rep = parsed.right.get

      rep.cpuCount mustEqual 2
      rep.cpuCoreCount mustEqual 12
      rep.hasHypthreadingEnabled must beFalse
      rep.cpuSpeed must beCloseTo(2.3, 0.1)

      rep.totalMemory.inGigabytes must beCloseTo(32L, 1)
      rep.memoryBanksUsed mustEqual 4
      rep.memoryBanksUnused mustEqual 8
      rep.memoryBanksTotal mustEqual 12

      rep.totalStorage.toHuman mustEqual "5.46 TB"
      rep.diskCount mustEqual 6

      rep.nicCount mustEqual 2
      rep.hasGbNic must beTrue
      rep.has10GbNic must beFalse
    }

    "Parse standard dell (AMD) lshw output with 10-Gig card" in {
      val tenGData = getResource("lshw-10g.xml")
      val parser = new LshwParser(tenGData)
      val parsed = parser.parse()
      parsed must beRight
      val rep = parsed.right.get

      rep.cpuCount mustEqual 2
      rep.cpuCoreCount mustEqual 12
      rep.hasHypthreadingEnabled must beFalse
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
    }

    "Parse softlayer supermicro (Intel) lshw output" in {
      val intelData = getResource("lshw-intel.xml")
      val parser = new LshwParser(intelData)
      val parsed = parser.parse()
      parsed must beRight
      val rep = parsed.right.get

      rep.cpuCount mustEqual 2
      rep.cpuCoreCount mustEqual 12
      rep.hasHypthreadingEnabled must beTrue
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
    }

    "Parse softlayer supermicro (Intel) lshw (old format) output" in {
      val oldData = getResource("lshw-old.xml")
      val parser = new LshwParser(oldData)
      val parsed = parser.parse()
      parsed must beRight
      val rep = parsed.right.get

      rep.cpuCount mustEqual 11
      rep.cpuCoreCount mustEqual 11
      rep.hasHypthreadingEnabled must beFalse
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
    }

  }

  def getResource(filename: String) = {
    val stream = getClass.getClassLoader.getResourceAsStream(filename)
    val tmp = io.Source.fromInputStream(stream)
    val str = tmp.mkString
    tmp.close()
    str
  }

}
