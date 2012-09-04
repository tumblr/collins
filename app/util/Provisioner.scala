package util

import play.api.{Play, Plugin}
import collins.provisioning.ProvisionerPlugin

object Provisioner {
  def pluginEnabled: Option[ProvisionerPlugin] = {
    Play.maybeApplication.flatMap { app =>
      app.plugin[ProvisionerPlugin].filter(_.enabled)
    }
  }

  def plugin: Option[ProvisionerPlugin] = pluginEnabled

  def pluginEnabled[T](fn: ProvisionerPlugin => T): Option[T] = {
    pluginEnabled.map { p =>
      fn(p)
    }
  }
}
