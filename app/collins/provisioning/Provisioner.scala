package collins.provisioning

import play.api.Logger
import collins.models.Asset
import collins.shell.CommandResult
import collins.cache.GuavaCacheFactory
import collins.shell.Command

trait Provisioner {
  protected[this] val logger = Logger(getClass)
  def profiles: Set[ProvisionerProfile]
  def canProvision(asset: Asset): Boolean
  def provision(request: ProvisionerRequest): CommandResult
  def test(request: ProvisionerRequest): CommandResult
  def profile(id: String): Option[ProvisionerProfile] = {
    profiles.find(_.identifier == id)
  }
  def makeRequest(token: String, id: String, notification: Option[String] = None, suffix: Option[String] = None): Option[ProvisionerRequest] = {
    profile(id).map { p =>
      ProvisionerRequest(token, p, notification, suffix)
    }
  }
}

object Provisioner extends Provisioner {

  protected[this] val profileCache =   
    GuavaCacheFactory.create(ProvisionerConfig.cacheSpecification, ProfileLoader())

  // overrides ProvisionerInterface.profiles
  override def profiles: Set[ProvisionerProfile] = {
    profileCache.get(ProvisionerConfig.profilesFile)
  }

  // overrides ProvisionerInterface.canProvision
  override def canProvision(asset: Asset): Boolean = {
    ProvisionerConfig.allowedStatus(asset.statusId) && ProvisionerConfig.allowedType(asset.assetTypeId)
  }

  // overrides ProvisionerInterface.provision
  override def provision(request: ProvisionerRequest): CommandResult = {
    val result = runCommand(command(request, ProvisionerConfig.command))
    if (result.exitCode != 0) {
      logger.warn("Command executed: %s".format(command(request, ProvisionerConfig.command)))
      logger.warn("Command code: %d, output %s".format(result.exitCode, result.stdout))
    }
    result
  }

  override def test(request: ProvisionerRequest): CommandResult = {
    val cmd = try command(request, ProvisionerConfig.checkCommand) catch {
      case _: Throwable => return CommandResult(0,"No check command specified")
    }
    val result = runCommand(cmd)
    if (result.exitCode != 0) {
      logger.warn("Command code: %d, output %s".format(result.exitCode, result.stdout))
    }
    result
  }

  protected def runCommand(cmd: String): CommandResult = {
    Command(Seq(cmd), logger).run()
  }

  protected def command(request: ProvisionerRequest, cmdString: Option[String]): String = {
    cmdString.map { cmd =>
      cmd.replace("<tag>", request.token)
        .replace("<profile-id>", request.profile.identifier)
        .replace("<notify>", request.notification.getOrElse(""))
        .replace("<suffix>", request.suffix.filter(_ => request.profile.allow_suffix).getOrElse(""))
        .replace("<logfile>", getLogLocation(request))
    }.getOrElse {
      throw new Exception("provisioner.command must be specified")
    }
  }

  private def getLogLocation(request: ProvisionerRequest): String = {
    val tmpDir = System.getProperty("java.io.tmpdir", "/tmp").stripSuffix("/")
    val filename = request.token.replaceAll("[^a-zA-Z0-9\\-]", "") + '-' + request.profile.identifier
    tmpDir + "/" + filename + ".log"
  }

}