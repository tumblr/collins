package com.tumblr.indefatigable

import org.specs.Specification
import com.tumblr.stats.Stat
import scala.Predef._

class FilterSpec extends Specification {
  "Filter" should {
    "create reasonable stats" in {
      "cpu-nice" >> {
        val stat = CollectdStat(
          Some("cpu"),
          Some("8"),
          Some("cpu"),
          Some("nice"),
          "wiktor-dev",
          1331028560,
          "value",
          759
        )

        (DefaultCollectdFilter(stat) == Stat[Double](1331028560, "cpu", 759, "wiktor-dev", Map("cpu_id"->"8", "cpu_state"->"nice"))).must(beTrue)
      }

      "cpu-freq" >> {
        val stat = CollectdStat(
          Some("cpufreq"),
          Some(""),
          Some("cpufreq"),
          Some("8"),
          "wiktor-dev",
          1331028560,
          "value",
          1.6
        )

        (DefaultCollectdFilter(stat) == Stat[Double](1331028560L, "cpu_freq", 1.6, "wiktor-dev", Map("cpu_id"->"8"))).must(beTrue)
      }

      "load" >> {
        val short = CollectdStat(Some("load"),None,Some("load"),None,"wiktor-dev",1331028500,"shortterm",0.4)
        (DefaultCollectdFilter(short) == Stat[Double](1331028500L, "load_short", 0.4 , "wiktor-dev", Map[String,String]())).must(beTrue)

        val mid   = CollectdStat(Some("load"),None,Some("load"),None,"wiktor-dev",1331028500,"midterm",  0.11)
        (DefaultCollectdFilter(mid)   == Stat[Double](1331028500L, "load_mid",   0.11, "wiktor-dev", Map[String,String]())).must(beTrue)

        val long  = CollectdStat(Some("load"),None,Some("load"),None,"wiktor-dev",1331028500,"longterm", 0.03)
        (DefaultCollectdFilter(long)  == Stat[Double](1331028500L, "load_long",  0.03, "wiktor-dev", Map[String,String]())).must(beTrue)
      }

      "interface" >> {
        val stat =  CollectdStat(Some("interface"),Some("lo"),Some("if_errors"),Some(""),"wiktor-dev",1331028560,"rx",0)
        (DefaultCollectdFilter(stat) == Stat[Double](1331028560, "if_errors", 0, "wiktor-dev", Map("iface" -> "lo", "if_dir" -> "rx"))).must(beTrue)
      }

      "swap" >> {
        val stat = CollectdStat(Some("swap"),Some(""),Some("swap"),Some("used"),"wiktor-dev",1331028560,"value",0.0)
        (DefaultCollectdFilter(stat) == Stat[Double](1331028560, "swap", 0.0, "wiktor-dev", Map("swap_state" -> "used"))).must(beTrue)
      }

      "memory" >> {
        val stat =  CollectdStat(Some("memory"),Some(""),Some("memory"),Some("buffered"),"wiktor-dev",1331028560,"value",5.33295104E8)
        (DefaultCollectdFilter(stat) == Stat[Double](1331028560, "mem", 5.33295104E8, "wiktor-dev", Map("mem_state" -> "buffered"))).must(beTrue)
      }

      "disk" >> {
        val stat = CollectdStat(Some("disk"),Some("sda"),Some("disk_octets"),Some(""),"wiktor-dev",1331028560,"read",2074202624)
        (DefaultCollectdFilter(stat) == Stat[Double](1331028560, "disk_octets", 2074202624, "wiktor-dev", Map("disk_dev" -> "sda", "disk_kind" -> "read"))).must(beTrue)
      }

      "default" >> {
        val stat = CollectdStat(Some("contextswitch"),Some(""),Some("contextswitch"),Some(""), "wiktor-dev", 1331028560, "value", 24350406302L)
        (DefaultCollectdFilter(stat) == Stat[Double](1331028560, "contextswitch", 24350406302L, "wiktor-dev", Map[String,String]())).must(beTrue)
      }
    }

    "handle unmatched filters" >> {
      (DefaultCollectdFilter(CollectdStat(Some("cpu"), None, None, None, "xxx", 1234, "notvalue", 123)) ==
        Stat[Double](1234, "cpu", 123, "xxx", Map("type" -> "notvalue"))).must(beTrue)
    }
  }
}
