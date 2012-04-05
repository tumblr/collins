package util
package parsers

import test.util.parsers.CommonParserSpec

import org.specs2._
import specification._
import matcher.Matcher

class LshwParserSpec extends mutable.Specification {

  def beNonEmptyStringSeq: Matcher[Seq[String]] = have(s => s.nonEmpty && s.size >= 5)

  class LshwParserHelper(val filename: String) extends Scope with CommonParserSpec[LshwRepresentation] {
    override def getParser(txt: String, options: Map[String,String] = Map.empty) =
      new LshwParser(txt, options)
    def parsed(options: Map[String,String] = Map.empty) = getParseResults(filename, options)
  }

  "The Lshw Parser" should {
   
    "Parse Dell (AMD) lshw output" in {

      "basic" in new LshwParserHelper("lshw-basic.xml") {
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
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
      }

      "with a virident card" in new LshwParserHelper("lshw-virident.xml") {
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
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
      }

      "with a 10-Gig card" in new LshwParserHelper("lshw-10g.xml") {
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
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
      }
    } // Parse dell (AMD) lshw output


    "Leverage config for flash disks" in {
      val file = "lshw-virident.xml"

      "Different flash description and size" in new LshwParserHelper(file) {
        val config = Map(
          "flashProduct" -> "flashmax",
          "flashSize" -> "1048576"
        )
        val parseResults = parsed(config)
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
          rep.hasFlashStorage must beTrue
          rep.totalFlashStorage.toHuman mustEqual "1.00 MB"
          rep.totalUsableStorage.toHuman mustEqual "278.47 GB"
        }
      }

      "Bad flash description" in new LshwParserHelper(file) {
        val config = Map(
          "flashProduct" -> "flashing memory",
          "flashSize" -> "1048576"
        )
        val parseResults = parsed(config)
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
          rep.hasFlashStorage must beFalse
          rep.totalFlashStorage.toHuman mustEqual "0 Bytes"
          rep.totalUsableStorage.toHuman mustEqual "278.46 GB"
        }
      }
    }

    "Parse softlayer supermicro (Intel) lshw output" in {
      "B.02.15 format" in new LshwParserHelper("lshw-intel.xml") {
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
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
      }

      "B.02.12 format" in new LshwParserHelper("lshw-old.xml") {
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
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
      } // B.02.12 format

      "A Production SL Web LSHW Output" in new LshwParserHelper("lshw-old-web.xml") {
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
          rep.cpuCount mustEqual 11
          rep.cpuCoreCount mustEqual 11
          rep.hasHyperthreadingEnabled must beFalse
          rep.cpuSpeed must beCloseTo(1.6, 0.1)

          rep.totalMemory.inGigabytes must beCloseTo(24L, 1)
          rep.memoryBanksUsed mustEqual 6
          rep.memoryBanksUnused mustEqual 6
          rep.memoryBanksTotal mustEqual 12

          rep.totalStorage.toHuman mustEqual "465.76 GB"
          rep.hasFlashStorage must beFalse
          rep.totalFlashStorage.toHuman mustEqual "0 Bytes"
          rep.diskCount mustEqual 2
          rep.hasCdRom must beTrue
    
          rep.nicCount mustEqual 4
          rep.hasGbNic must beTrue
          rep.has10GbNic must beFalse
          rep.macAddresses must have length 4
          rep.macAddresses must beNonEmptyStringSeq
        }
      } // B.02.12 format

    } // Parse softlayer supermicro (Intel) lshw output"

  } // The LSHW parser should

}
