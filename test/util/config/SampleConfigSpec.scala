package util
package config

import org.specs2.specification.Scope
import org.specs2.mutable._

import java.io.File
import scala.collection.JavaConverters._
import com.typesafe.config._

class SampleConfigurable() extends MessageHelper("sample") with Configurable {
  override val namespace = "sample"
  override val referenceConfigFilename = "test/resources/config/reference_sampleconfig.conf"

  override protected def validateConfig() {
  }

  def strict = getBoolean("strict", false)
  def name = getString("name")
  def stringItems = getStringList("string_items")
  def intItems = getIntList("int_items")

  def getPools = getObjectMap("pools")

  def getTags = getObjectMap("tagdecorators")

  // use for things that shouldn't be changed later on without a restart
  lazy val hostname = getString("hostname")
}

class SampleConfigSpec extends Specification {
  val sample1file = "test/resources/config/sampleconfig1.conf"

  "Configurable" should {
    "support defaults" in {
      val cfg = new SampleConfigurable
      val file = new File(sample1file)
      cfg.onChange(file)
      println(cfg.strict)
      println(cfg.name)
      println(cfg.stringItems)
      println(cfg.intItems)
      println(cfg.getPools)
      println(cfg.getTags)
      false
    }
    "support required and validated params" in {
      pending
    }
  }
}

