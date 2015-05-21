package collins.provisioning

import play.api.Application
import play.api.Plugin

import collins.cache.ConfigCache
import collins.models.Asset
import collins.shell.Command
import collins.shell.CommandResult


class ProvisionerPlugin(app: Application) extends Plugin with Provisioner {

  lazy protected[this] val profileCache =
    ConfigCache.create(ProvisionerConfig.cacheTimeout, ProfileLoader())

  // overrides Plugin.enabled
  override def enabled: Boolean = {
    ProvisionerConfig.pluginInitialize(app.configuration)
    ProvisionerConfig.enabled
  }

  // overrides Plugin.onStart
  override def onStart() {
    if (enabled) {
      ProvisionerConfig.validateConfig
    }
  }

  // overrides Plugin.onStop
  override def onStop() {
  }

  // overrides ProvisionerInterface.profiles
  override def profiles: Set[ProvisionerProfile] = {
    profileCache.get(ProvisionerConfig.profilesFile)
  }

  // overrides ProvisionerInterface.canProvision
  override def canProvision(asset: Asset): Boolean = {
    ProvisionerConfig.allowedStatus(asset.status) && ProvisionerConfig.allowedType(asset.asset_type)
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
