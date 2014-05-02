package collins.provisioning

import org.specs2.mutable._
import java.io.File

class ProfileLoaderSpec extends Specification {
  val profiles = {
    val file = new File("test/resources/profiles.yaml")
    ProfileLoader.fromFile(file)
  }
  "The test profiles.yaml should load" should {
    "all profiles" >> {
      profiles.size === 38
    }
    "a profile with all values specified" >> {
      val profile = profiles.find(_.identifier == "searchnode").get
      profile.label === "Search Server"
      profile.prefix === "search"
      profile.allow_suffix === true
      val role = profile.role
      role.primary_role === Some("SEARCH")
      role.pool === Some("SEARCH_POOL")
      role.secondary_role === Some("MASTER")
      role.requires_primary_role === false
      role.requires_secondary_role === true
      role.requires_pool === false
      role.attributes === Map()
      role.clear_attributes === Set()
    }
    "a profile with mostly defaults" >> {
      val profile = profiles.find(_.identifier == "webnode").get
      profile.label === "Web Server"
      profile.prefix === "web"
      profile.allow_suffix === false
      val role = profile.role
      role.primary_role === None
      role.secondary_role === None
      role.pool === None
      role.requires_primary_role === true
      role.requires_secondary_role === true
      role.requires_pool === true
      role.attributes === Map()
      role.clear_attributes === Set()
    }
    "a profile with custom attributes" >> {
      val profile = profiles.find(_.identifier == "testattributesnode").get
      profile.label === "Test Custom Attributes Node"
      val role = profile.role
      val addattrs = role.attributes
      addattrs.keys.size === 4
      addattrs.get("SUPER_IMPORTANT_TAG").get === "true"
      addattrs.get("LOWER_CASE_ATTRIBUTE").get === "Hello world"
      addattrs.get("NUMERIC_ATTRIBUTE").get === "123"
      addattrs.get("NODECLASS").get === "shouldnt be set to this"
      val clearattrs = role.clear_attributes
      clearattrs === Set("NODECLASS","DELETE_ME","SUPER_DANGEROUS_TAG","DUPLICATE_ATTRIBUTE")
    }
  }
}
