package util

import org.specs2.specification.Scope
import org.specs2.mutable._
import org.specs2.mock._

trait ToMock {
  def verified: Boolean = true
}

class FeatureSpec extends Specification with Mockito {

  "Feature Configurations" should {
    "Implement" in {
      "isSet" in {
        Feature("foo", source = Map("features.foo" -> "stuff")).isSet must beTrue
        Feature("bar", source = Map("features.foo" -> "stuff")).isSet must beFalse
      }
      "isUnset" in {
        Feature("foo", source = Map("features.foo" -> "stuff")).isUnset must beFalse
        Feature("bar", source = Map("features.foo" -> "stuff")).isUnset must beTrue
      }
      "enabled" in {
        Feature("foo", source = Map("features.foo" -> "true")).enabled must beTrue
        Feature("foo", source = Map("features.foo" -> "false")).enabled must beFalse
        Feature("foo", source = Map("features.bar" -> "true")).enabled must beFalse
        Feature("bar", source = Map("features.foo" -> "true")).enabled must beFalse
      }
      "disabled" in {
        Feature("foo", source = Map("features.foo" -> "true")).disabled must beFalse
        Feature("foo", source = Map("features.foo" -> "false")).disabled must beTrue
        Feature("foo", source = Map("features.bar" -> "true")).disabled must beTrue
        Feature("bar", source = Map("features.foo" -> "true")).disabled must beTrue
      }
    } // Implement
    "Convert" in {
      "toBoolean" in {
        Feature("foo", source = Map("features.bar" -> "true")).toBoolean(true) must beTrue
        Feature("foo", source = Map("features.bar" -> "true")).toBoolean(false) must beFalse
        Feature("foo", source = Map("features.foo" -> "true")).toBoolean(true) must beTrue
        Feature("foo", source = Map("features.foo" -> "false")).toBoolean(true) must beFalse
        Feature("foo", source = Map("features.foo" -> "true")).toBoolean(false) must beTrue
        Feature("foo", source = Map("features.foo" -> "false")).toBoolean(false) must beFalse
      }
      "toMap" in {
        val config = Map(
          "lshw.description" -> "Description here",
          "lshw.enabled"     -> "true",
          "globalthing"      -> "indeed",
          "other.thing"      -> "123"
        )
        val cfg = Config(config)
        cfg.getString("globalthing", "stuff").toString mustEqual "indeed"
        cfg.getBoolean("lshw", "enabled") mustEqual Some(true)
        val lshw = cfg.get("lshw")
        lshw must beSome
        lshw.get.getBoolean("enabled") mustEqual Some(true)
        val lshwMap = cfg.toMap("lshw")
        lshwMap must havePairs("description" -> "Description here", "enabled" -> "true")
      }
      "toSet" in {
        Feature("foo", source = Map("features.foo" -> "Fizz,Buzz, Baz, Blam"))
          .toSet(false) must contain("Fizz", "Buzz", "Baz", "Blam")
        Feature("foo", source = Map("features.foo" -> "Fizz,Buzz, Baz, Blam"))
          .toSet must contain("FIZZ", "BUZZ", "BAZ", "BLAM")
        Feature("foo", source = Map("features.stuff" -> "thing")).toSet must be empty
      }
    } // Convert
    "Handle Bad Data" in {
      "BooleanFormat" in {
        Feature("foo", source = Map("features.foo" -> "lkajsd")).enabled must throwA[BooleanFormatException]
      }
      "IntegerFormat" in {
        Feature("foo", source = Map("features.foo" -> "lkasjd")).toInt(23) must throwA[IntegerFormatException]
      }
      "SetFormat" in {
        Feature("foo", source = Map("features=foo" -> "s")).toSet must throwA[CompoundFormatException]
      }
      "StringFormat" in {
        Feature("foo", source = Map("features=foo" -> "s")).getString("") must throwA[StringFormatException]
      }
    } // Handle in
    "Provide a fluid interface" in {
      "ifSet" in {
        "is true" in {
          Feature("foo", source = Map("features.foo" -> "23")).ifSet { f =>
            f.toInt(-1)
          }.getOrElse(17) mustEqual 23
        }
        "is false" in {
          Feature("foo", source = Map("features.bar" -> "19")).ifSet { f =>
            f.toInt(-1)
          }.getOrElse(17) mustEqual 17
        }
      }
      "whenEnabled is true" in new mockfeature {
        Feature("foo", source = Map("features.foo" -> "true")).whenEnabled {
          featureMock.verified
        }.orElse {
          failure("features.foo enabled but whenEnabled not executed")
        }
        there was one(featureMock).verified
      }
      "whenEnabled is false" in new mockfeature {
        Feature("foo", source = Map("features.foo" -> "false")).whenEnabled {
          failure("features.foo disabled but whenEnabled executed")
        }.orElse {
          featureMock.verified
        }
        there was one(featureMock).verified
      }
      "whenEnabledOrUnset is unset" in new mockfeature {
        Feature("foo", source = Map("features.bar" -> "true")).whenEnabledOrUnset {
          featureMock.verified
        }
        there was one(featureMock).verified
      }
      "whenEnableOrUnset is set but false" in new mockfeature {
        Feature("foo", source = Map("features.foo" -> "false")).whenEnabledOrUnset {
          failure("features.foo is disabled but enabled/unset fired")
        }.orElse {
          featureMock.verified
        }
        there was one(featureMock).verified
      }
    } // Provide a fluid interface
    "Allow returns from functions" in {
      def testMethod(enabled: Boolean): Int = {
        Feature("foo", source = Map("features.foo" -> enabled.toString)).whenEnabledOrUnset {
          return 1
        }
        return 0
      }
      testMethod(true) mustEqual 1
      testMethod(false) mustEqual 0
    }
  }

  trait mockfeature extends Scope {
    val featureMock = mock[ToMock]
    featureMock.verified returns true
  }
}
