package util
package views

import play.api.Configuration
import play.api.mvc.Content
import play.api.templates.Html

import scala.collection.JavaConversions._
import scala.util.matching.Regex

import models.{Asset, AssetMeta, AssetView, Page}
import util.power.{PowerComponent, PowerUnits}

case class FormatConfigurationException(formatter: String, tag: String)
  extends Exception("Didn't find formatter Formatter.%s in configuration for %s"
    .format(formatter, tag))

case class MethodCallConfigurationException(methodCall: String, tag: String)
  extends Exception("Didn't find method call %s in configuration for %s"
    .format(methodCall, tag))

case class ShowIfConfigurationException(method: String, tag: String)
  extends Exception("Didn't find method %s in configuration for %s"
    .format(method, tag))

/**
 * Helper methods used when compiling lists of assets within Collins, with
 * facilities for determining whether tags should be shown and how to display
 * metavalue information from these tags.
 */
object ListHelper extends DecoratorBase {

  /**
   * Specifies the default ordering for asset list views.
   */
  val DEFAULT_TAG_ORDER = "~tag,HOSTNAME,PRIMARY_ROLE,STATUS,~created,~updated"

  /**
   * Specifies a regex used to find method calls to make during string
   * formatting.
   */
  val METHOD_CALL_REGEX = """~\{(.*)\}""".r

  /**
   * Specifies a prefix which indicates a call to a method instead of performing
   * a metavalue tag lookup.
   */
  val METHOD_PREFIX = "~"

  val DEFAULT_VALUE = "<em>Undefined</em>"

  /**
   * Supplies a configuration prefix to all decorator-specific methods.
   *
   * @return a String containing the list tag-specific configuration prefix.
   */
  override def configPrefix(): String = "listtags"

  /**
   * Calls a method specified as a string, using the supplied arguments to
   * determine the manner in which it gets called.
   *
   * @param method a String containing a method to call.
   * @param asset an AssetView object.
   * @return the results of the method call.
   */
  def callMethod(method: String, onObj: Object, args: Object*) = {
    val parameterClasses = args.map{ arg => arg.getClass }
    val methodSplit = method.split("\\.")
    if (methodSplit.length > 1) {
      val methodClass = methodSplit.slice(0, methodSplit.length - 1)
        .reduceLeft(_ + "." + _)
      val classMethod = methodSplit(methodSplit.length - 1)
      onObj.getClass.getClassLoader.loadClass(methodClass)
        .getMethod(classMethod, parameterClasses : _*).invoke(onObj, args : _*)
    } else {
      onObj.getClass.getMethod(method, parameterClasses : _*).invoke(onObj,
          args : _*)
    }
  }

  /**
   * Decorates the value of an asset, allowing for methods from the asset's
   * context to fill strings matching ~{<stuff>}.
   *
   * @param key a String containing an asset metavalue tag or method call
   * @param value the value of the asset metavalue tag or method call
   * @param asset an AssetView corresponding to the value being decorated.
   * @return a Play Content object suitable for rendering.
   */
  def decorate(key: String, value: String, asset: AssetView): Content = {
    // Replaces all instances of strings matching ~{methodCall} with the
    // results of calling ListHelper.<methodCall>(asset), to allow for the
    // dynamic substitution of instance-specific data into decorated strings.
    var decoratedString = getDecorator(key) match {
      case None => value
      case Some(d) => d.format(key, value)
    }
    METHOD_CALL_REGEX.findAllIn(decoratedString).matchData.foreach{ found =>
      val methodCall = found.group(1)
      try {
        decoratedString = decoratedString.replace("~{%s}".format(methodCall),
            this.callMethod(methodCall, this, asset).toString())
      } catch {
        case nsme: NoSuchMethodException =>
          throw MethodCallConfigurationException(methodCall, key)
      }
    }
    Html(decoratedString)
  }

  /**
   * Formats and decorates the value of an asset's metadata tag for HTML
   * display by the configuration specified within the Collins config file.
   *
   * @param tag a String containing an asset metadata tag.
   * @param asset the AssetView corresponding to the asset.
   * @return a Play Content object containing the metadata tag's value.
   */
  def decorateTagValueForAsset(tag: String, asset: AssetView): Content = {
    val formattedVal = formatTagValueForAsset(tag, asset)
    val decorator = Config.getString("listtags.%s.decorator".format(tag), "")
    if (!decorator.isEmpty()) {
      decorate(tag, formattedVal, asset)
    } else {
      Html(formattedVal)
    }
  }

  /**
   * Formats the text value of an asset's metadata tag by the configuration
   * specified within the Collins config file.
   *
   * @param tag a String containing an asset metadata tag.
   * @param asset the AssetView corresponding to the asset.
   * @return a formatted String containing the metadata tag's value.
   */
  def formatTagValueForAsset(tag: String, asset: AssetView): String = {
    val tagValue = getTagValueForAsset(tag, asset)
    val formatter = Config.getString("listtags.%s.formatter".format(tag), "")
    if (!formatter.isEmpty() && tagValue != None) {
      try {
        this.callMethod(formatter, Formatter, tagValue).toString()
      } catch {
        case nsme: NoSuchMethodException =>
          throw FormatConfigurationException(formatter, tag)
      }
    } else if (tagValue == None) {
      val default = Config.getString("listtags.%s.default".format(tag), "")
      if (!default.isEmpty()) {
        default
      } else {
        Config.getString("listtags.all.default", DEFAULT_VALUE)
      }
    } else {
      tagValue.toString()
    }
  }

  /**
   * Returns the URL where an asset can be found.
   *
   * @param asset an AssetView representing an asset
   * @return a String containing an URL to the supplied Asset
   */
  def getAssetURL(asset: Asset): String = {
    asset.remoteHost.getOrElse("") + app.routes.CookieApi.getAsset(asset.tag)
  }

  /**
   * Returns the header for the column of asset metadata dictated by a metadata
   * tag or method call.  If a metadata tag, retrieves tag's label and returns,
   * otherwise uses the optional header configuration value, specified by the
   * Collins configuration.  Failing this, returns the tag/method call passed
   * in.
   *
   * @param tag a String containing a metadata tag or method call
   * @return a String containing the column header for this tag/method call
   */
  def getColumnHeader(tag: String): String = {
    if (!tag.contains(METHOD_PREFIX)) {
      val assetMeta = AssetMeta.findByName(tag)
      if (assetMeta != None) {
        return assetMeta.get.getLabel()
      }
    }
    val header = Config.getString("listtags.%s.header".format(tag), "")
    if (!header.isEmpty()) {
      return header
    }
    tag
  }

  /**
   * Returns an ordered list of the asset tags to use when listing assets, as
   * specified within the Collins configuration file.
   *
   * @return an ordered list of Strings containing tags from assets
   */
  def getListHeaderTags(): Seq[String] = {
    Config.getString("listtags.all.order", DEFAULT_TAG_ORDER).split(",")
  }

  def getPowerComponentsInOrder(units: PowerUnits): Seq[PowerComponent] = {
    val components = units.flatMap { unit =>
      unit.components
    }
    components.toSeq.sorted
  }

  def getPowerComponentsInOrder(): Seq[PowerComponent] = {
    getPowerComponentsInOrder(PowerUnits())
  }

  /**
   * Returns the value of an asset's metadata call or, if the tag is prefixed
   * with a ~, the value of calling <asset object>.<tag>().  If no data is
   * found, returns an empty string.
   *
   * @param tag a String containing an asset metadata tag or asset method call
   * @return the value of the asset metadata tag or asset method call.
   */
  def getTagValueForAsset(tag: String, asset: AssetView) = {
    // If the tag starts with the method prefix, call the method on the asset
    // object instead of performing an asset metadata lookup.
    if (tag.contains(METHOD_PREFIX)) {
      val method = tag.split(METHOD_PREFIX)(1)
      val retVal = this.callMethod(method, asset)
      retVal match {
        case Some(value) => value.asInstanceOf[Object]
        case None => None
        case _ => retVal
      }
    } else {
      val metaValue = Asset.findByTag(asset.tag).get.getMetaAttribute(tag)
      if (metaValue != None) {
        metaValue.get.getValue
      } else {
        None
      }
    }
  }

  /**
   * Renders an asset's SoftLayer link into HTML form.
   *
   * @param asset an AssetView to retrieve a SoftLayer link for.
   * @return a Play Content object representing the SoftLayer link
   */
  def renderSoftLayerLink(asset: AssetView): Content = {
    this.getClass.getClassLoader.loadClass("views.html.asset.slLink").getMethod(
        "render", classOf[AssetView], classOf[String]).invoke(this, asset, "")
      .asInstanceOf[Content]
  }

  /**
   * Returns whether an asset's metadata tag's header should be shown.
   *
   * @param tag a String containing an asset's metadata tag.
   * @param assets a Page of AssetView objects, representing the assets found.
   * @return a Boolean representing whether this column should be shown.
   */
  def showColumnForTag(tag: String, assets: Page[AssetView]): Boolean = {
    val method = Config.getString("listtags.%s.showif".format(tag), "")
    if (!method.isEmpty()) {
      // Calls the user-supplied method to check whether column should be shown.
      try {
        this.callMethod(method, this, assets).asInstanceOf[Boolean]
      } catch {
        case nsme: NoSuchMethodException =>
          throw ShowIfConfigurationException(method, tag)
      }
    } 
    if (!tag.contains(METHOD_PREFIX)) {
      // If the tag is not present within the db/cache, don't display column.
      if (AssetMeta.findByName(tag) == None) {
        return false
      }
    }
    true
  }

  /**
   * Returns whether the Hostname column should be shown for a list of assets.
   *
   * @param assets a Page of AssetView objects, representing the assets to be
   *   listed.
   * @return a Boolean representing whether the Hostname column should be shown.
   */
  def showHostname(assets: Page[AssetView]): Boolean = {
    assets.items.find(_.getHostnameMetaValue.isDefined).map(_ => true)
      .getOrElse(false)
  }

  /**
   * Returns whether the SoftLayer link column should be shown.
   *
   * @param assets a Page of AssetView objects
   * @return whether SoftLayer link should be shown for these assets.
   */
  def showSoftLayerLink(assets: Page[AssetView]): Boolean = {
    SoftLayer.pluginEnabled { plugin =>
      assets.items.collectFirst{
        case asset: Asset if(plugin.isSoftLayerAsset(asset)) => true
      }.getOrElse(false)
    }.getOrElse(false)
  }

}
