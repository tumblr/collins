package util

import models.{Asset, User}
import play.api.{Configuration, Mode, Play}

// Globally useful configurations
object AppConfig extends Config {
  val IgnoredAssets = Feature("ignoreDangerousCommands").toSet

  // Ignore asset for dangerous commands
  def ignoreAsset(tag: String): Boolean = IgnoredAssets(tag.toUpperCase)
  def ignoreAsset(asset: Asset): Boolean = ignoreAsset(asset.tag)

  val IpmiKey = "ipmi"
  def ipmi: Option[Configuration] = Config.get(IpmiKey)
  def ipmiMap = Config.toMap(IpmiKey)

  def isProd() = getApplicationMode() == Mode.Prod
  def getApplicationMode(): Mode.Mode = {
    Play.maybeApplication.map { app =>
      app.mode
    }.getOrElse(Mode.Dev)
  }

  private val UserSession = new ThreadLocal[Option[User]] {
    override def initialValue(): Option[User] = None
  }

  def setUser(user: Option[User]) = UserSession.set(user)
  def getUser(): Option[User] = UserSession.get()
  def removeUser() = UserSession.remove()
}
