package util

import models.{Asset, User}
import config.Feature
import play.api.{Configuration, Mode, Play}
import play.api.i18n.Messages

// Globally useful configurations
object AppConfig {

  def ignoredAssets = Feature.ignoreDangerousCommands.map(_.toUpperCase)

  // Ignore asset for dangerous commands
  def ignoreAsset(tag: String): Boolean = ignoredAssets.contains(tag.toUpperCase)
  def ignoreAsset(asset: Asset): Boolean = ignoreAsset(asset.tag)

  def isProd() = getApplicationMode() == Mode.Prod
  def isDev() = getApplicationMode() == Mode.Dev
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
