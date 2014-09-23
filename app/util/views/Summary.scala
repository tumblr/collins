package util
package views

import models.Asset

import play.api.mvc.Content
import play.api.templates.Html

trait Summarizer[T] {
  type Token = String
  type Tokenizer = T => Token

  val replacementMap: Map[Token,Tokenizer]
  val template: String
  val openMustach: String = "{"
  val closeMustach: String = "}"

  def get(t: T): Content = Html(
    replacementMap.foldLeft(template) { case(txt, kv) =>
      val (key, fn) = kv
      txt.replace(getKey(key), fn(t))
    }
  )
  protected def getKey(key: String) = {
    openMustach + key + closeMustach
  }
}

case class AssetSummary(template: String) extends Summarizer[Asset] {
  override val replacementMap = Map(
    "assetType" -> getAssetType _,
    "hostname" -> getHostname _
  )

  def getAssetType(asset: Asset): String = {
    asset.isServerNode match {
      case true => "Server"
      case false => asset.getType().label
    }
  }
  def getHostname(asset: Asset): String = {
    asset.getMetaAttribute("HOSTNAME").map(_.getValue).getOrElse(asset.tag)
  }
}

object Summary {
  def apply(asset: Asset): Content = {
    val template = "<h1>{assetType} <small>{hostname}</small></h1>"
    AssetSummary(template).get(asset)
  }
}
