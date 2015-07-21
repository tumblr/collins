package collins.util

import power._

import org.specs2.specification.Scope
import org.specs2.mutable._
import org.specs2.matcher.DataTables

class PowerUnitSpec extends Specification with DataTables {

  val DefaultUnitsRequired = 2
  val Strip = 'STRIP
  val Outlet = 'OUTLET
  val Pdu = 'PDU
  val DefaultComponents = Set(Strip.name, Outlet.name, Pdu.name)
  val DefaultRequires = Set(Strip.name)

  case class PowerUnitScope(
    components: Set[String] = DefaultComponents,
    count: Int = DefaultUnitsRequired,
    alphabetic: Boolean = true,
    requires: Set[String] = DefaultRequires
  ) extends Scope {
    lazy val config = PowerConfiguration(count, alphabetic, components, requires)
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

  "Power Configuration" should {
    "handle unit counts" >> {
      "count" || "throwsException" |
      -1      !! true              |
      0       !! false             |
      1       !! false             |
      20      !! true              |> {
      (count, throwsException) =>
        val config = new PowerUnitScope(count = count)
        if (throwsException) {
          config.config must throwA[InvalidPowerConfigurationException]
        } else {
          config.config.unitsRequired mustEqual count
        }
      }
    }
    "handle multiple component types" >> {
      "name"    || "symbol"    |
      "strips"  !! Strip.name  |
      "outlets" !! Outlet.name |
      "pdus"    !! Pdu.name    |> {
      (name, symbolName) =>
        val symbol = Symbol(symbolName)
        val enabled = new PowerUnitScope(requires = Set())
        enabled.size(symbol) mustEqual DefaultUnitsRequired
        val disabled = new PowerUnitScope(components = DefaultComponents - symbolName, requires = Set())
        disabled.size(symbol) mustEqual 0
      }
    }
    "fail if components are badly specified" >> {
      "input set"                           |
      Set.empty[String]                     |
      Set(Strip.name, "", Outlet.name)      |
      Set(Strip.name, " ", Outlet.name)     |> {
      (inputSet) =>
        val config = new PowerUnitScope(components = inputSet)
        config.config must throwA[InvalidPowerConfigurationException].like {
          case e => e.getMessage must contain(PowerConfiguration.Messages.ComponentsUnspecified)
        }
      }
    }
  }

  "Power Components" should {
    "have predictable alphabetic names" in new PowerUnitScope(count = 2, alphabetic = true) {
      var found = 0
      units.toSeq.zipWithIndex.foreach { case(unit, id) =>
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
    
    "have predictable numeric names" in new PowerUnitScope(count = 2, alphabetic = false) {
      strip(units.head).sid.toInt mustEqual 0
      outlet(units.head).sid.toInt mustEqual 0
      pdu(units.head).sid.toInt mustEqual 0
      strip(units.last).sid.toInt mustEqual 1
      outlet(units.last).sid.toInt mustEqual 1
      pdu(units.last).sid.toInt mustEqual 1
    }
  }

  "Power Units" should {
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
    "support equality properly" in new PowerUnitScope() {
      val unit1 = PowerUnit(config, 0)
      val unit2 = PowerUnit(config, 1)
      val unit3 = PowerUnit(new PowerUnitScope(count = 10).config, 0)
      Set(unit1, unit3).size mustEqual 1
      unit1 mustNotEqual unit2
    }
    "support ordering correctly" in new PowerUnitScope(count = 10) {
      val punits = (0 until config.unitsRequired).map(PowerUnit(config, _))
      val shuffled = doUntil(scala.util.Random.shuffle(punits)) { sunits =>
        sunits.head.id > sunits.last.id
      }
      val unshuffled = PowerUnits(shuffled)
      val powerIds = for (unit <- unshuffled; component <- unit) yield(unit.id)
      powerIds must contain(exactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)).inOrder
    }
  }

  "Power Unit Validation" should {
    type ComponentKeys = Tuple6[String,String,String,String,String,String]
    def componentKeys(cfg: PowerUnitScope, unit1: PowerUnit, unit2: PowerUnit): ComponentKeys = {
      (
        cfg.strip(unit1).key, cfg.outlet(unit1).key, cfg.pdu(unit1).key,
        cfg.strip(unit2).key, cfg.outlet(unit2).key, cfg.pdu(unit2).key
      )
    }
    def mapFromKeys(keys: ComponentKeys): Map[String,String] = {
      val (u1sk, u1ok, u1pk, u2sk, u2ok, u2pk) = keys
      Map(
        u1sk -> "strip 1",
        u1ok -> "outlet 1",
        u1pk -> "pdu 1",
        u2sk -> "strip 2",
        u2ok -> "outlet 2",
        u2pk -> "pdu 2"
      )
    }
    "fail if required components are missing" in new PowerUnitScope() {
      val ck = componentKeys(this, PowerUnit(config, 0), PowerUnit(config, 1))
      val (u1sk, u1ok, u1pk, u2sk, u2ok, u2pk) = ck
      val map = mapFromKeys(ck) - u2ok
      PowerUnits.validateMap(map, config) must throwA[InvalidPowerConfigurationException].like {
        case InvalidPowerConfigurationException(msg, key) => msg must contain("Missing")
      }
    }
    "fail if duplicate values on a unique component are found" in new PowerUnitScope() {
      val ck = componentKeys(this, PowerUnit(config, 0), PowerUnit(config, 1))
      val (u1sk, u1ok, u1pk, u2sk, u2ok, u2pk) = ck
      val map = mapFromKeys(ck) + (u2sk -> "strip 1")
      PowerUnits.validateMap(map, config) must throwA[InvalidPowerConfigurationException].like {
        case InvalidPowerConfigurationException(msg, key) => msg must contain("Duplicate")
      }
    }
    "succeed when all is cool" in new PowerUnitScope() {
      val ck = componentKeys(this, PowerUnit(config, 0), PowerUnit(config, 1))
      val (u1sk, u1ok, u1pk, u2sk, u2ok, u2pk) = ck
      val map = mapFromKeys(ck)
      PowerUnits.validateMap(map, config) must not throwA
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
