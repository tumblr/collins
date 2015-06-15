package collins.util.views

import play.twirl.api.Content
import play.twirl.api.Html

import collins.models.Asset

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
    "hostname" -> getHostname _,
    "statusLabel" -> getStatusLabel _
  )

  def getAssetType(asset: Asset): String = {
    asset.isServerNode match {
      case true => "Server"
      case false => asset.getType().label
    }
  }
  def getStatusLabel(asset: Asset): String = {
    val state_string = asset.getState match {
      case Some(st) => ":%s" format st.name
      case None => ""
    }
    val state_descr = asset.getState match {
      case Some(st) => " - %s" format st.description
      case None => ""
    }
    val badge_class = asset.getStatus.name match {
      case "Allocated"                    => "label-primary"
      case "Cancelled" | "Decommissioned" => "label-default"
      case "Maintenance"                  => "label-danger"
      case "Incomplete" | "New"           => "label-warning"
      case "Provisioned" | "Provisioning" => "label-info"
      case "Unallocated"                  => "label-success"
      case _                              => "label-default"
    }
    "<span data-rel=\"tooltip\" data-original-title=\"" + asset.getStatus.description + state_descr +
      "\" data-placement=\"bottom\"  class=\"label " + badge_class + "\">" + asset.getStatusName + state_string + "</span>"
  }
  def getHostname(asset: Asset): String = {
    asset.getMetaAttribute("HOSTNAME").map(_.getValue).getOrElse(asset.tag)
  }
}

object Summary {
  def apply(asset: Asset): Content = {
    val template = "<h1>{assetType} <small>{hostname}</small></h1><h4 class=\"asset-status-label\">{statusLabel}</h4>"
    AssetSummary(template).get(asset)
  }
}
