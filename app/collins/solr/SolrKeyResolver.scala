package collins.solr

import models.{Asset, AssetFinder, AssetMeta, AssetMetaValue, AssetType, IpAddresses, MetaWrapper, Page, PageParams, State, Status, Truthy}
import models.asset.AssetView
import models.IpmiInfo.Enum._


import Solr.AssetSolrDocument
import AssetMeta.ValueType
import AssetMeta.ValueType._

//some light DSL's for making solr key flags easier to read

trait SolrKeyFlag {
  def boolValue: Boolean
}

object SolrKeyFlag {

  implicit def namedboolean2boolean(n: SolrKeyFlag): Boolean = n.boolValue

  sealed trait IsDynamic extends SolrKeyFlag
  case object Dynamic extends SolrKeyFlag {
    val boolValue = true
  }
  case object Static extends SolrKeyFlag {
    val boolValue = false
  }

  sealed trait IsMultiValued extends SolrKeyFlag
  case object MultiValued extends SolrKeyFlag {
    val boolValue = true
  }
  case object SingleValued extends SolrKeyFlag {
    val boolValue = false
  }

  sealed trait IsSortable extends SolrKeyFlag
  case object Sortable extends SolrKeyFlag {
    val boolValue = true
  }
  case object NotSortable extends SolrKeyFlag {
    val boolValue = false
  }
}
import SolrKeyFlag._



/** 
 * This class holds data about a solr key, mainly for translating "local" key
 * names to their solr equivalent
 */
case class SolrKey (
  val name: String,
  val valueType: ValueType,
  val isDynamic: Boolean,
  val isMultiValued: Boolean,
  val isSortable: Boolean,
  val aliases: Set[String] = Set()
) {
  require(!(isMultiValued && isSortable), "Cannot create sortable multivalue keys (yet)")
  require(name.toUpperCase == name, "Name must be ALL CAPS")
  require(aliases.foldLeft(true){(b, al) => b && al == al.toUpperCase}, "Aliases must be ALL CAPS")

  lazy val resolvedName = name + (if(isDynamic) ValueType.postFix(valueType) else "")
  def isAliasOf(alias: String) = aliases(alias.toUpperCase)

  def matches(k: String) = (k.toUpperCase == name) || isAliasOf(k)

  /**
   * returns true if wildcards should be automatically applied to unquoted values of this key (only relevant for string values)
   */
  def autoWildcard = !(SolrKeyResolver.noAutoWildcardKeys contains name)

  def sortName = name.toUpperCase + "_SORT"

  def sortKey = SolrKey(sortName, String, Static, SingleValued, Sortable)

  def sortify(value: SolrValue): Option[(SolrKey, SolrStringValue)] = value match {
    case s:SolrSingleValue if (isSortable) => Some(sortKey, SolrStringValue(s.value.toString, StrictUnquoted))
    case _ => None
  }

}


/**
 * Mixin for enum keys, allows us to resolve the solr key and then validate
 * the enum value by passing it to the valueLookup method.
 */
trait EnumKey{ self: SolrKey =>
  def lookupByName(value: String): Option[String]
  def lookupById(value: Int): Option[String]
}

trait SolrKeyResolver {
  import SolrKeyResolver._
  def apply(rawKey: String): Option[SolrKey] = allDocKeys.find{_ matches rawKey.toUpperCase} orElse docSpecificKey(rawKey)

  def either(_rawkey: String) = apply(_rawkey) match {
    case Some(k) => Right(k)
    case None => Left("Unknown key " + _rawkey)
  }

  def docSpecificKey(rawKey: String): Option[SolrKey]

}

object SolrKeyResolver {
  /**
   * This is a list of keys that should not add pre/post wildcards when the
   * value in CQL is unquoted with no modifiers.  For example, normally if you
   * do foo = bar in CQL, it is translated to foo:*bar*, but we don't want to
   * do that for these keys
   *
   * This is extremely important because otherwise if you search for ip_address
   * = 192.168.1.1, which you assume is a specific ip address, you will
   * actually get all assets 192.168.1.1xx addressess.
   *
   * TODO: create config option to specify additional tags (it should be
   * required for these)
   */
  val noAutoWildcardKeys = List("TAG", "IP_ADDRESS", "IPMI_ADDRESS", "MESSAGE")

  val allDocKeys = List(
    SolrKey("DOC_TYPE", String, Static, SingleValued, Sortable),
    SolrKey("LAST_INDEXED", String, Static, SingleValued, Sortable),
    SolrKey("UUID", String, Static, SingleValued, Sortable)
  )


}



object AllDocKeyResolver extends SolrKeyResolver {
  def docSpecificKey(nope: String) = None
}
