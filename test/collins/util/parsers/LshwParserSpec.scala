package collins.util.parsers

import org.specs2.matcher.Matcher
import org.specs2.mutable

import play.api.test.FakeApplication
import play.api.test.WithApplication

import collins.util.LshwRepresentation

class LshwParserSpec extends mutable.Specification {

  def beNonEmptyStringSeq: Matcher[Seq[String]] = { s:Seq[String] =>
    s.forall { x => x.nonEmpty && x.size >= 5 }
  }

  class LshwParserHelper(val filename: String, val ac: Map[String, _ <: Any] = Map.empty)
    extends WithApplication(FakeApplication(additionalConfiguration = ac)) with CommonParserSpec[LshwRepresentation] {
    override def getParser(txt: String) =
      new LshwParser(txt)
    def parsed() = getParseResults(filename)
  }

  "The Lshw Parser" should {

    "Parse Dell (AMD) lshw output" in {

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
          rep.nicsBySpeedGb(1).size mustEqual 2
          rep.nicsBySpeedGb(10).size mustEqual 1
          rep.macAddresses must have length 3
          rep.macAddresses must beNonEmptyStringSeq

          rep.base.product mustEqual "PowerEdge C6105 (N/A)"
          rep.base.vendor mustEqual "Winbond Electronics"
          rep.base.serial.get mustEqual "FZ1NXQ1"
        }
      } // with a 10-gig card

      "quad nic missing capacity having size" in new LshwParserHelper("lshw-size.xml") {
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
          rep.nicCount mustEqual 4
          rep.nicsBySpeedGb(1).size mustEqual 2
          rep.nicsBySpeedGb(10).size mustEqual 2
          rep.macAddresses must have length 4
          rep.macAddresses must beNonEmptyStringSeq

          rep.base.product mustEqual "PowerEdge C6220 II (N/A)"
          rep.base.vendor mustEqual "Dell Inc."
        }
      }

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
          rep.nicsBySpeedGb(1).size mustEqual 2
          rep.nicsBySpeedGb get 10 must beNone
          rep.macAddresses must have length 2
          rep.macAddresses must beNonEmptyStringSeq
        }
      } // basic

      "quad nic" in new LshwParserHelper("lshw-quad.xml") {
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
          rep.cpuCount mustEqual 2
          rep.cpuCoreCount mustEqual 12
          rep.hasHyperthreadingEnabled must beFalse
          rep.cpuSpeed must beCloseTo(2.3, 0.1)

          rep.totalMemory.inGigabytes must beCloseTo(96L, 1)
          rep.memoryBanksUsed mustEqual 12
          rep.memoryBanksUnused mustEqual 0
          rep.memoryBanksTotal mustEqual 12

          rep.totalStorage.toHuman mustEqual "931.52 GB"
          rep.diskCount mustEqual 2

          rep.hasFlashStorage must beFalse
          rep.totalFlashStorage.toHuman mustEqual "0 Bytes"
          rep.totalUsableStorage.toHuman mustEqual "931.52 GB"

          rep.nicCount mustEqual 6
          rep.nicsBySpeedGb(1).size mustEqual 6
          rep.nicsBySpeedGb get 10 must beNone
          rep.macAddresses must have length 6
          rep.macAddresses must beNonEmptyStringSeq
        }
      }

      "quad nic missing capacity" in new LshwParserHelper("lshw-quad-no-capacity.xml", Map("lshw.defaultNicCapacity" -> "10000000000")) {
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
          rep.cpuCount mustEqual 2
          rep.cpuCoreCount mustEqual 12
          rep.hasHyperthreadingEnabled must beFalse
          rep.cpuSpeed must beCloseTo(2.3, 0.1)

          rep.totalMemory.inGigabytes must beCloseTo(96L, 1)
          rep.memoryBanksUsed mustEqual 12
          rep.memoryBanksUnused mustEqual 0
          rep.memoryBanksTotal mustEqual 12

          rep.totalStorage.toHuman mustEqual "931.52 GB"
          rep.diskCount mustEqual 2

          rep.hasFlashStorage must beFalse
          rep.totalFlashStorage.toHuman mustEqual "0 Bytes"
          rep.totalUsableStorage.toHuman mustEqual "931.52 GB"

          rep.nicCount mustEqual 6
          rep.nicsBySpeedGb(1).size mustEqual 5
          // collins should assume 10g capacity for the nic that isnt reporting it
          rep.nicsBySpeedGb(10).size mustEqual 1
          rep.macAddresses must have length 6
          rep.macAddresses must beNonEmptyStringSeq
        }
      } // basic

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

          rep.hasFlashStorage must beTrue
          rep.totalFlashStorage.toHuman mustEqual "1.27 TB"
          rep.totalUsableStorage.toHuman mustEqual "1.55 TB"

          rep.totalStorage.toHuman mustEqual "278.46 GB"
          rep.diskCount mustEqual 4

          rep.nicCount mustEqual 2
          rep.nicsBySpeedGb(1).size mustEqual 2
          rep.nicsBySpeedGb get 10 must beNone
          rep.macAddresses must have length 2
          rep.macAddresses must beNonEmptyStringSeq
        }
      }

      "with a nvme flash card" in new LshwParserHelper("lshw-nvme.xml") {
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

          rep.hasFlashStorage must beTrue
          rep.totalFlashStorage.toHuman mustEqual "1.27 TB"
          rep.totalUsableStorage.toHuman mustEqual "1.55 TB"

          rep.totalStorage.toHuman mustEqual "278.46 GB"
          rep.diskCount mustEqual 4

          rep.nicCount mustEqual 2
          rep.nicsBySpeedGb(1).size mustEqual 2
          rep.nicsBySpeedGb get 10 must beNone
          rep.macAddresses must have length 2
          rep.macAddresses must beNonEmptyStringSeq
        }
      }

    } // Parse dell (AMD) lshw output

    "Parse different versioned formats" in {
      "B.02.12 format" in new LshwParserHelper("lshw-old.xml") {
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
          rep.cpuCount mustEqual 2
          rep.cpuCoreCount mustEqual 2
          rep.hasHyperthreadingEnabled must beFalse
          rep.cpuSpeed must beCloseTo(1.6, 0.1)

          rep.totalMemory.inGigabytes must beCloseTo(72L, 1)
          rep.memoryBanksUsed mustEqual 18
          rep.memoryBanksUnused mustEqual 0
          rep.memoryBanksTotal mustEqual 18

          rep.totalStorage.toHuman mustEqual "930.99 GB"
          rep.diskCount mustEqual 3

          rep.nicCount mustEqual 4
          rep.nicsBySpeedGb(1).size mustEqual 4
          rep.nicsBySpeedGb get 10 must beNone
          rep.macAddresses must have length 4
          rep.macAddresses must beNonEmptyStringSeq

          rep.base.product mustEqual "X8DTN"
          rep.base.vendor mustEqual "Supermicro"
        }
      } // B.02.12 format

      "B.02.14 format" in new LshwParserHelper("lshw-b0214.xml") {
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
          rep.cpuCount mustEqual 2
          rep.cpuCoreCount mustEqual 2
          rep.hasHyperthreadingEnabled must beFalse
          rep.cpuSpeed must beCloseTo(2.3, 0.1)

          rep.totalMemory.inGigabytes must beCloseTo(96L, 1)
          rep.memoryBanksUsed mustEqual 12
          rep.memoryBanksUnused mustEqual 0
          rep.memoryBanksTotal mustEqual 12

          rep.totalStorage.toHuman mustEqual "931.52 GB"
          rep.diskCount mustEqual 2

          rep.nicCount mustEqual 6
          rep.nicsBySpeedGb(1).size mustEqual 6
          rep.nicsBySpeedGb get 10 must beNone
          rep.macAddresses must have length 6
          rep.macAddresses must beNonEmptyStringSeq
        }
      }

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
          rep.nicsBySpeedGb(1).size mustEqual 4
          rep.nicsBySpeedGb get 10 must beNone
          rep.macAddresses must have length 4
          rep.macAddresses must beNonEmptyStringSeq
        }
      }

      "B.02.16 format" in new LshwParserHelper("lshw-b0216.xml") {
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

          rep.totalStorage.toHuman mustEqual "465.76 GB"
          rep.hasFlashStorage must beFalse
          rep.totalFlashStorage.toHuman mustEqual "0 Bytes"
          rep.diskCount mustEqual 1
          rep.hasCdRom must beFalse

          rep.nicCount mustEqual 2
          rep.nicsBySpeedGb(1).size mustEqual 2
          rep.nicsBySpeedGb get 10 must beNone
          rep.macAddresses must have length 2
          rep.macAddresses must beNonEmptyStringSeq

          rep.base.product mustEqual "PowerEdge C6105 (N/A)"
          rep.base.vendor mustEqual "Dell Inc."

        }
      }
    }

    "Leverage config for flash disks" in {
      "Different flash description and size" in new LshwParserHelper("lshw-virident.xml", Map(
          "lshw.flashProduct" -> "flashmax",
          "lshw.flashSize" -> "1048576"
        )) {
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
          rep.hasFlashStorage must beTrue
          rep.totalFlashStorage.toHuman mustEqual "1.00 MB"
          rep.totalUsableStorage.toHuman mustEqual "278.47 GB"
        }
      }

      /*"Bad flash description" in new LshwParserHelper("lshw-bad-flash.xml", Map(
          "lshw.flashProduct" -> "flashing memory",
          "lshw.flashSize" -> "1048576"
        )) {
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
          rep.hasFlashStorage must beFalse
          rep.totalFlashStorage.toHuman mustEqual "0 Bytes"
          rep.totalUsableStorage.toHuman mustEqual "278.46 GB"
        }
      }*/
    }

    "Parse softlayer supermicro (Intel) lshw output" in {
      "A Production SL Web LSHW Output" in new LshwParserHelper("lshw-old-web.xml") {
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
          rep.cpuCount mustEqual 2
          rep.cpuCoreCount mustEqual 2
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
          rep.nicsBySpeedGb(1).size mustEqual 4
          rep.nicsBySpeedGb get 10 must beNone
          rep.macAddresses must have length 4
          rep.macAddresses must beNonEmptyStringSeq
        }
      } // B.02.12 format

      "A New Production SL Web LSHW Output" in new LshwParserHelper("lshw-new-web-old-lshw.xml") {
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
          rep.cpuCount mustEqual 2
          rep.cpuCoreCount mustEqual 2
          rep.hasHyperthreadingEnabled must beFalse
          rep.cpuSpeed must beCloseTo(2.0, 0.1)

          rep.totalMemory.inGigabytes must beCloseTo(32L, 1)
          rep.memoryBanksUsed mustEqual 8
          rep.memoryBanksUnused mustEqual 16
          rep.memoryBanksTotal mustEqual 24

          rep.totalStorage.toHuman mustEqual "465.76 GB"
          rep.hasFlashStorage must beFalse
          rep.totalFlashStorage.toHuman mustEqual "0 Bytes"
          rep.diskCount mustEqual 2
          rep.hasCdRom must beTrue

          rep.nicCount mustEqual 4
          rep.nicsBySpeedGb(1).size mustEqual 4
          rep.nicsBySpeedGb get 10 must beNone
          rep.macAddresses must have length 4
          rep.macAddresses must beNonEmptyStringSeq
        }
      } // B.02.12 format

    } // Parse softlayer supermicro (Intel) lshw output"

    "Handle missing fields in LSHW" in {
      "No base serial" in new LshwParserHelper("missing-base-serial.xml") {
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
          rep.base.product mustEqual "Virtual Machine (None)"
          rep.base.vendor mustEqual "Microsoft Corporation"
          rep.base.serial mustEqual None
          rep.base.description mustEqual "Desktop Computer"
        }
      }
    }

    "Parse Dell LSHW Output" in {
      "R620 LSHW Output" in new LshwParserHelper("lshw-dell-r620-single-cpu.xml") {
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
          rep.cpuCount mustEqual 1
          rep.cpuCoreCount mustEqual 8
          rep.hasHyperthreadingEnabled must beTrue
          rep.cpuSpeed must beCloseTo(2.0, 0.1)

          rep.totalMemory.inGigabytes must beCloseTo(32L, 1)
          rep.memoryBanksUsed mustEqual 4
          rep.memoryBanksUnused mustEqual 20
          rep.memoryBanksTotal mustEqual 24

          rep.nicCount mustEqual 4
          rep.nicsBySpeedGb(1).size mustEqual 4
          rep.nicsBySpeedGb get 10 must beNone
          rep.macAddresses must have length 4
          rep.macAddresses must beNonEmptyStringSeq

          rep.base.product mustEqual "PowerEdge R620 ()"
          rep.base.vendor mustEqual "Winbond Electronics"
        }
      }
      "with LVM disk" in new LshwParserHelper("lshw-lvm.xml") {
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
          rep.cpuCount mustEqual 1
          rep.cpuCoreCount mustEqual 8
          rep.hasHyperthreadingEnabled must beTrue
          rep.cpuSpeed must beCloseTo(2.0, 0.1)

          rep.totalMemory.inGigabytes must beCloseTo(32L, 1)
          rep.memoryBanksUsed mustEqual 4
          rep.memoryBanksUnused mustEqual 20
          rep.memoryBanksTotal mustEqual 24

          rep.totalStorage.toHuman mustEqual "381.94 GB"
          rep.diskCount mustEqual 2

          rep.hasFlashStorage must beFalse
          rep.totalFlashStorage.toHuman mustEqual "0 Bytes"
          rep.totalUsableStorage.toHuman mustEqual "381.94 GB"

          rep.nicCount mustEqual 4
          rep.nicsBySpeedGb(1).size mustEqual 4
          rep.nicsBySpeedGb get 10 must beNone
          rep.macAddresses must have length 4
          rep.macAddresses must beNonEmptyStringSeq
        }
      } // LVM
    }

    "parse amd-opteron-wonky" in {
      "wonky amd-opteron output" in new LshwParserHelper("lshw-amd-opteron-wonky.xml") {
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
          rep.cpuCount mustEqual 1
          rep.cpuCoreCount mustEqual 1 // This really has 6, but sadly lshw has no clue
          rep.hasHyperthreadingEnabled must beFalse
          rep.cpuSpeed must beCloseTo(2.8, 0.1)

          rep.totalMemory.inGigabytes must beCloseTo(16L, 1)
          rep.memoryBanksUsed mustEqual 4
          rep.memoryBanksUnused mustEqual 8
          rep.memoryBanksTotal mustEqual 12

          rep.nicCount mustEqual 2
          rep.nicsBySpeedGb(1).size mustEqual 2
          rep.nicsBySpeedGb get 10 must beNone
          rep.macAddresses must have length 2
          rep.macAddresses must beNonEmptyStringSeq

          rep.base.description mustEqual "Multi-system"
          rep.base.product mustEqual "ProLiant SL335s G7"
          rep.base.vendor mustEqual "HP"
          }
       }

      "wonky amd-opteron output w/ show empty sockets" in new LshwParserHelper("lshw-amd-opteron-wonky.xml", Map(
          "lshw.includeEmptySocket" -> "true"
        )) {
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
          rep.cpuCount mustEqual 2
        }
      }
    } // wonky opterons

    "Parse GPU server" in {
      "dual nvidia GPU server" in new LshwParserHelper("lshw-gpu.xml"){
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
          rep.cpuCount mustEqual 2
          rep.cpuCoreCount mustEqual 12

          rep.gpuCount mustEqual 2
        }
      }
      "Dell single nvidia GPU server" in new LshwParserHelper("lshw-dell.xml"){
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
          rep.cpuCount mustEqual 1
          rep.cpuCoreCount mustEqual 4

          rep.gpuCount mustEqual 1
        }
      }
    }

    "Identify Motherboard and Firmware info" in {
      "from a Dell workstation" in new LshwParserHelper("lshw-dell.xml") {
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
          rep.base.motherboard mustEqual "0M91XC"
          rep.base.firmware mustEqual "1.9.5"
        }
      }

      "from a Supermicro server" in new LshwParserHelper("lshw-intel.xml") {
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
          rep.base.motherboard mustEqual "X8DTN"
          rep.base.firmware mustEqual "080016"
        }
      }

      "from the dynamic enum server lshw" in new LshwParserHelper("lshw-basic-dynamic-enums.xml") {
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
          rep.base.motherboard mustEqual "001V46"
          rep.base.firmware mustEqual "1.7.6"
        }
      }

      "from a HP server that doesn't haven a motherboard version" in new LshwParserHelper("lshw-amd-opteron-wonky.xml") {
        val parseResults = parsed()
        parseResults must beRight
        parseResults.right.toOption must beSome.which { rep =>
          rep.base.motherboard mustEqual ""
          rep.base.firmware mustEqual "A24 (02/04/2011)"
        }
      }
    }
  } // The LSHW parser should
}
