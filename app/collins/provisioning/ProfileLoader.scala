package collins.provisioning

import collins.validation.File
import com.tumblr.play.interop.ProvisionerProfileHelper

import play.api.Logger
import com.google.common.cache.CacheLoader
import java.io.{File => IoFile}

case class ProfileLoader(profiles: Set[ProvisionerProfile])
  extends CacheLoader[String, Set[ProvisionerProfile]]
{
  private[this] val logger = Logger("collins.provisioning.ProfileLoader")

  override def load(filename: String): Set[ProvisionerProfile] = {
    logger.debug("Refreshing profiles from %s".format(filename))
    try {
      ProfileLoader.fromFile(new IoFile(filename))
    } catch {
      case e =>
        logger.error("There is a problem with the profiles file %s: %s".format(
          filename, e.getMessage
        ))
        profiles
    }
  }
}

object ProfileLoader {
  import scala.collection.JavaConverters._
import scala.collection.immutable.SortedSet

  def apply(): ProfileLoader = {
    val profileFile = ProvisionerConfig.profilesFile
    val profiles = fromFile(new IoFile(profileFile))
    ProfileLoader(profiles)
  }
  def fromFile(filename: String): Set[ProvisionerProfile] = {
    fromFile(new IoFile(filename))
  }
  def fromFile(file: IoFile): Set[ProvisionerProfile] = {
    File.requireFileIsReadable(file)
    val p = ProvisionerProfileHelper.fromFile(file)
    require(p != null, "Invalid yaml file %s".format(file.getAbsolutePath))
    SortedSet(p.profiles.asScala.map { case(key, profile) =>
      val roleData = ProvisionerRoleData(
        Option(profile.primary_role), Option(profile.pool), Option(profile.secondary_role),
        Option(profile.requires_primary_role).getOrElse(true),
        Option(profile.requires_pool).getOrElse(true),
        Option(profile.requires_secondary_role).getOrElse(false)
      )
      val label = requireNonNullNonEmpty("label", profile.label)
      val prefix = requireNonNullNonEmpty("prefix", profile.prefix)
      ProvisionerProfile(key, label, prefix, profile.allow_suffix, roleData)
    }.toSeq:_*)
  }

  protected def requireNonNullNonEmpty(name: String, v: String): String = {
    Option(v).filter(_.nonEmpty).getOrElse {
      throw new Exception("%s must not be null/empty".format(name))
    }
  }
}
