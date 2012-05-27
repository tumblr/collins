package util

import power._

import org.specs2.specification.Scope
import org.specs2.mutable._
import org.specs2.mock._

class PowerUnitSpec extends test.ApplicationSpecification with Mockito {

  val DefaultUnitsRequired = 2
  val Strip = 'STRIP
  val Outlet = 'OUTLET
  val Pdu = 'PDU
  val DefaultComponents = Set(Strip.name, Outlet.name, Pdu.name)

  case class PowerUnitScope(
    components: Set[String] = DefaultComponents,
    count: Int = DefaultUnitsRequired,
    alphabetic: Boolean = true
  ) extends Scope {
    lazy val config = PowerConfiguration(count, alphabetic, components)
    def units(): PowerUnits = PowerUnits(config)
    def ids() = units.flatMap(_.components.map(_.sid))
    def strip(s: PowerUnit) = s.component(Strip).get
    def size(ct: Symbol) = units.map(_.component(ct)).filter(_.isDefined).size
    def strips() = units.map(strip(_))
    def outlet(s: PowerUnit) = s.component(Outlet).get
    def outlets() = units.map(outlet(_))
    def pdu(s: PowerUnit) = s.component(Pdu).get
    def pdus() = units.map(pdu(_))
  }

  "Power Units" should {
    "provide configurable" in {
      "unit counts" in {
        "-1" in new PowerUnitScope(count = -1) {
          config must throwA[IllegalArgumentException]
        }
        "0" in new PowerUnitScope(count = 0) {
          config.unitsRequired mustEqual 0
        }
        "1" in new PowerUnitScope(count = 1) {
          config.unitsRequired mustEqual 1
        }
        "20" in new PowerUnitScope(count = 20) {
          config must throwA[IllegalArgumentException]
        }
      }
      "strips" in {
        "enabled" in new PowerUnitScope() {
          size(Strip) mustEqual DefaultUnitsRequired
        }
        "disabled" in new PowerUnitScope(components = DefaultComponents - Strip.name) {
          size(Strip) mustEqual 0
        }
      }
      "outlets" in {
        "enabled" in new PowerUnitScope() {
          size(Outlet) mustEqual DefaultUnitsRequired
        }
        "disabled" in new PowerUnitScope(components = DefaultComponents - Outlet.name) {
          size(Outlet) mustEqual 0
        }
      }
      "pdus" in {
        "enabled" in new PowerUnitScope() {
          size(Pdu) mustEqual DefaultUnitsRequired
        }
        "disabled" in new PowerUnitScope(components = DefaultComponents - Pdu.name) {
          size(Pdu) mustEqual 0
        }
      }
      "naming" in {
        "that has ids to use" in new PowerUnitScope() {
          ids.size must beGreaterThan(0)
        }
        "alphabetic" in new PowerUnitScope(alphabetic = true) {
          ids must beMatching("^[A-Za-z]+$").forall
        }
        "numeric" in new PowerUnitScope(alphabetic = false) {
          ids must beMatching("^[^A-Za-z]+$").forall
        }
      }
    }
    "convert from a request map" in new PowerUnitScope() {
      val unit1 = PowerUnit(config, 0)
      val unit2 = PowerUnit(config, 1)
      val (u1sk, u1ok, u1pk, u2sk, u2ok, u2pk) = (
        strip(unit1).key, outlet(unit1).key, pdu(unit1).key,
        strip(unit2).key, outlet(unit2).key, pdu(unit2).key
      )
      val map = Map(
        "fizz" -> Seq("buzz"),
        u1sk -> Seq("strip 1"),
        u1ok -> Seq("outlet 1"),
        u1pk -> Seq("pdu 1"),
        u2sk -> Seq("strip 2"),
        u2ok -> Seq("outlet 2"),
        u2pk -> Seq("pdu 2"),
        "flim" -> Seq("flam")
      )
      map.keys.size mustEqual 8
      val newMap = PowerUnits.unitMapFromMap(map, config)
      newMap.keys.size mustEqual 6
      newMap must havePairs(
        u1sk -> "strip 1",
        u1ok -> "outlet 1",
        u1pk -> "pdu 1",
        u2sk -> "strip 2",
        u2ok -> "outlet 2",
        u2pk -> "pdu 2"
      )
    }
  }

  "Power Unit" should {
    "support equality properly" in new PowerUnitScope() {
      val unit1 = PowerUnit(config, 0)
      val unit2 = PowerUnit(config, 1)
      val unit3 = PowerUnit(PowerUnitScope(count = 10).config, 0)
      Set(unit1, unit3).size mustEqual 1
      unit1 mustNotEqual unit2
    }
    "support naming properly" in {
      "alphabetic" in new PowerUnitScope(count = 2, alphabetic = true) {
        var found = 0
        units.zipWithIndex.foreach { case(unit, id) =>
          val sid = id match {
            case 0 => "A"
            case 1 => "B"
          }
          strip(unit).sid mustEqual sid
          outlet(unit).sid mustEqual sid
          pdu(unit).sid mustEqual sid
          found += 1
        }
        found mustEqual 2
      }
      "numeric" in new PowerUnitScope(count = 2, alphabetic = false) {
        strip(units.head).sid.toInt mustEqual 0
        outlet(units.head).sid.toInt mustEqual 0
        pdu(units.head).sid.toInt mustEqual 0
        strip(units.last).sid.toInt mustEqual 1
        outlet(units.last).sid.toInt mustEqual 1
        pdu(units.last).sid.toInt mustEqual 1
      }
    }
    "support ordering correctly" in new PowerUnitScope(count = 10) {
      val punits = (0 until config.unitsRequired).map(PowerUnit(config, _))
      val shuffled = doUntil(scala.util.Random.shuffle(punits)) { sunits =>
        sunits.head.id > sunits.last.id
      }
      val unshuffled = PowerUnits(shuffled)
      val powerIds = for (unit <- unshuffled; component <- unit) yield(unit.id)
      powerIds must contain(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).only.inOrder
    }
    "support labels via messages" in new PowerUnitScope() {
      units must have(!strip(_).label.startsWith(config.parentKey))
      units must have(!outlet(_).label.startsWith(config.parentKey))
      units must have(!pdu(_).label.startsWith(config.parentKey))
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
