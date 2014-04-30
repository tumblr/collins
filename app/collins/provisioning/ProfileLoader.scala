package collins.provisioning

import collins.validation.File
import com.tumblr.play.interop.{JProfile, ProvisionerProfileHelper}
import scala.collection.JavaConverters._

import play.api.Logger
import com.google.common.cache.CacheLoader
import scala.collection.immutable.SortedSet
import java.io.{File => IoFile}

case class ProfileLoader(profiles: Set[ProvisionerProfile])
  extends CacheLoader[String, Set[ProvisionerProfile]]
{
  private[this] val logger = Logger("collins.provisioning.ProfileLoader")

  override def load(filename: String): Set[ProvisionerProfile] = {
    logger.info("Refreshing profiles from %s".format(filename))
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
    val classLoader = try {
      import play.api.Play.current
      current.classloader
    } catch {
      case e =>
        getClass.getClassLoader
    }
    val p = ProvisionerProfileHelper.fromFile(file, classLoader)
    require(p != null, "Invalid yaml file %s".format(file.getAbsolutePath))
    val seq = p.profiles.asScala.map { case(key, profile) =>
      val roleData = ProvisionerRoleData(
        optionalButNonEmpty(profile.getPrimary_role()),
        optionalButNonEmpty(profile.getPool()),
        optionalButNonEmpty(profile.getSecondary_role()),
        optionalButNonEmpty(profile.getContact()),
        optionalButNonEmpty(profile.getContact_notes()),
        Option(profile.getAttributes().asScala.map(a => (a._1.toUpperCase, a._2.toString)).toMap),
        Option(profile.getClear_attributes().asScala.map(_.toUpperCase).toSet),
        Option(profile.getRequires_primary_role()).map(_.booleanValue()).getOrElse(true),
        Option(profile.getRequires_pool()).map(_.booleanValue()).getOrElse(true),
        Option(profile.getRequires_secondary_role()).map(_.booleanValue()).getOrElse(false)
      )
      val label = requireNonNullNonEmpty("label", profile.getLabel())
      val prefix = requireNonNullNonEmpty("prefix", profile.getPrefix())
      ProvisionerProfile(key, label, prefix, profile.getAllow_suffix().booleanValue(), roleData)
    }.toSeq
    SortedSet(seq:_*)
  }

  protected def optionalButNonEmpty(v: String): Option[String] = {
    Option(v).filter(_.nonEmpty)
  }
  protected def requireNonNullNonEmpty(name: String, v: String): String = {
    Option(v).filter(_.nonEmpty).getOrElse {
      throw new Exception("%s must not be null/empty".format(name))
    }
  }
}
