package util

import org.specs2.mutable._

class StorageUnitSpec extends Specification {

  "Bit StorageUnit" should {
    val ONE_THOUSAND: Long = 1000
    val ONE_MILLION: Long = 1000*ONE_THOUSAND
    val ONE_BILLION: Long = 1000*ONE_MILLION

    "Display in human readable format" in {
      "Bits" >> {
        BitStorageUnit(999).toHuman mustEqual("999 Bits")
      }
      "Kb" >> {
        BitStorageUnit(1000).toHuman mustEqual("1.00 Kb")
        BitStorageUnit(1101).toHuman mustEqual("1.10 Kb")
        BitStorageUnit(100000).toHuman mustEqual("100.00 Kb")
      }
      "Mb" >> {
        BitStorageUnit(ONE_MILLION).toHuman mustEqual("1.00 Mb")
      }
      "Gb" >> {
        BitStorageUnit(ONE_BILLION).toHuman mustEqual("1.00 Gb")
        BitStorageUnit(10*ONE_BILLION).toHuman mustEqual("10.00 Gb")
        BitStorageUnit(100*ONE_BILLION).toHuman mustEqual("100.00 Gb")
      }
      "Tb" >> {
        BitStorageUnit(1000*ONE_BILLION).toHuman mustEqual("1.00 Tb")
      }
    }
  }

  "Byte StorageUnit" should {
    val ONE_THOUSAND: Long = 1024
    val ONE_MILLION: Long = 1024*ONE_THOUSAND
    val ONE_BILLION: Long = 1024*ONE_MILLION

    "Display in human readable format" in {
      "Bytes" >> {
        ByteStorageUnit(100).toHuman mustEqual("100 Bytes")
      }
      "KB" >> {
        ByteStorageUnit(1024).toHuman mustEqual("1.00 KB")
        ByteStorageUnit(1124).toHuman mustEqual("1.10 KB")
        ByteStorageUnit(102400).toHuman mustEqual("100.00 KB")
      }
      "MB" >> {
        ByteStorageUnit(ONE_MILLION).toHuman mustEqual("1.00 MB")
      }
      "GB" >> {
        ByteStorageUnit(ONE_BILLION).toHuman mustEqual("1.00 GB")
        ByteStorageUnit(10*ONE_BILLION).toHuman mustEqual("10.00 GB")
        ByteStorageUnit(100*ONE_BILLION).toHuman mustEqual("100.00 GB")
      }
      "TB" >> {
        ByteStorageUnit(1024*ONE_BILLION).toHuman mustEqual("1.00 TB")
      }
    }
  }

}
