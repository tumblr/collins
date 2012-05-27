package util

import power._

import org.specs2.specification.Scope
import org.specs2.mutable._
import org.specs2.mock._

class PowerUnitSpec extends test.ApplicationSpecification with Mockito {

  val DefaultUnitsRequired = 2

  case class PowerUnitScope(
    count: Int = DefaultUnitsRequired,
    requiresStrip: Boolean = true,
    requiresOutlet: Boolean = true,
    alphabetic: Boolean = true
  ) extends Scope {
    def ifHave(comp: Boolean, s: String) = if (comp) { Set(s) } else {Set.empty[String]}
    val components = ifHave(requiresStrip, "STRIP") ++ ifHave(requiresOutlet, "OUTLET")
    lazy val config = PowerUnitConfig(count, components, alphabetic)
    def units(): PowerUnits = PowerUnits(config)
    def strip(s: PowerUnit) = s.strip.get
    def outlet(s: PowerUnit) = s.outlet.get
  }

  "Power Units" should {
    "provide configurable" in {
      "unit counts" in {
        "-1" in new PowerUnitScope(-1) {
          config must throwA[IllegalArgumentException]
        }
        "0" in new PowerUnitScope(0) {
          config.unitsRequired mustEqual 0
        }
        "1" in new PowerUnitScope(1) {
          config.unitsRequired mustEqual 1
        }
        "20" in new PowerUnitScope(20) {
          config must throwA[IllegalArgumentException]
        }
      }
      "strips" in {
        "enabled" in new PowerUnitScope(requiresStrip = true) {
          units().strips().size mustEqual DefaultUnitsRequired
        }
        "disabled" in new PowerUnitScope(requiresStrip = false) {
          units().strips().size mustEqual 0
        }
      }
      "outlets" in {
        "enabled" in new PowerUnitScope(requiresOutlet = true) {
          units().outlets().size mustEqual DefaultUnitsRequired
        }
        "disabled" in new PowerUnitScope(requiresOutlet = false) {
          units().outlets().size mustEqual 0
        }
      }
      "naming" in {
        "that has ids to use" in new PowerUnitScope() {
          units().ids().size must beGreaterThan(0)
        }
        "alphabetic" in new PowerUnitScope(alphabetic = true) {
          units().ids() must beMatching("^[A-Za-z]+$").forall
        }
        "numeric" in new PowerUnitScope(alphabetic = false) {
          units().ids() must beMatching("^[^A-Za-z]+$").forall
        }
      }
    }
    "convert from a request map" in new PowerUnitScope() {
      val unit1 = PowerUnit(0, config)
      val unit2 = PowerUnit(1, config)
      val map = Map(
        "fizz" -> Seq("buzz"),
        unit1.strip.get.key -> Seq("strip 1"),
        unit1.outlet.get.key -> Seq("outlet 1"),
        unit2.strip.get.key -> Seq("strip 2"),
        unit2.outlet.get.key -> Seq("outlet 2"),
        "flim" -> Seq("flam")
      )
      map.keys.size mustEqual 6
      val newMap = PowerUnits.unitMapFromMap(map, config)
      newMap.keys.size mustEqual 4
      newMap must havePairs(
        unit1.strip.get.key -> "strip 1",
        unit1.outlet.get.key -> "outlet 1",
        unit2.strip.get.key -> "strip 2",
        unit2.outlet.get.key -> "outlet 2"
      )
    }
  }

  "Power Unit" should {
    "support equality properly" in new PowerUnitScope() {
      val unit1 = PowerUnit(0, config)
      val unit2 = PowerUnit(1, config)
      val unit3 = PowerUnit(0, PowerUnitScope(10).config)
      Set(unit1, unit3).size mustEqual 1
      unit1 mustNotEqual unit2
    }
    "support naming properly" in {
      "alphabetic" in new PowerUnitScope(count = 2, alphabetic = true) {
        strip(units().head).sid mustEqual "A"
        outlet(units().head).sid mustEqual "A"
        strip(units().last).sid mustEqual "B"
        outlet(units().last).sid mustEqual "B"
      }
      "numeric" in new PowerUnitScope(count = 2, alphabetic = false) {
        strip(units().head).sid.toInt mustEqual 0
        outlet(units().head).sid.toInt mustEqual 0
        strip(units().last).sid.toInt mustEqual 1
        outlet(units().last).sid.toInt mustEqual 1
      }
    }
    "support ordering correctly" in new PowerUnitScope(count = 10) {
      val punits = (0 until config.unitsRequired).map(PowerUnit(_, config))
      val shuffled = doUntil(scala.util.Random.shuffle(punits)) { sunits =>
        sunits.head.id > sunits.last.id
      }
      PowerUnits(shuffled).map(_.id) must contain(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).only.inOrder
    }
    "support labels via messages" in new PowerUnitScope() {
      units() must have(!strip(_).label.startsWith(config.parentKey))
      units() must have(!outlet(_).label.startsWith(config.parentKey))
    }
  }

  def doUntil[T](value: => T)(checkF: T => Boolean): T = {
    val computedValue = value
    if (checkF(computedValue)) {
      computedValue
    } else {
      doUntil(value)(checkF)
    }
  }

}
