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
        Feature("foo", source = Map("features.foo" -> "true")).isSet must beTrue
        Feature("bar", source = Map("features.foo" -> "true")).isSet must beFalse
      }
      "isUnset" in {
        Feature("foo", source = Map("features.foo" -> "true")).isUnset must beFalse
        Feature("bar", source = Map("features.foo" -> "true")).isUnset must beTrue
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
      "toBoolean" in {
        Feature("foo", source = Map("features.bar" -> "true")).toBoolean(true) must beTrue
        Feature("foo", source = Map("features.bar" -> "true")).toBoolean(false) must beFalse
        Feature("foo", source = Map("features.foo" -> "true")).toBoolean(true) must beTrue
        Feature("foo", source = Map("features.foo" -> "false")).toBoolean(true) must beFalse
        Feature("foo", source = Map("features.foo" -> "true")).toBoolean(false) must beTrue
        Feature("foo", source = Map("features.foo" -> "false")).toBoolean(false) must beFalse
      }
    } // Implement in
    "Handle" in {
      "Bad data" in {
        Feature("foo", source = Map("features.foo" -> "lkajsd")).isSet must throwA[Exception]
        Feature("foo", source = Map("features.foo" -> "lkajsd")).enabled must throwA[Exception]
      }
    } // Handle in
    "Provide a fluid interface" in {
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
