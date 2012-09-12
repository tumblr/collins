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
      profiles.size === 37
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
    }
  }
}
