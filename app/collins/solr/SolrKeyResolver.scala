package collins.solr

import models.{Asset, AssetFinder, AssetMeta, AssetMetaValue, AssetType, IpAddresses, MetaWrapper, Page, PageParams, State, Status, Truthy}
import models.asset.AssetView
import models.IpmiInfo.Enum._

import play.api.Logger

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
 * A helpful type to ensure strings are always capitalized
 *
 * WARNING, since == is not typed, doing StringValue == UpperCaseStringValue
 * will always return false!!! Therefore, always make sure any function that
 * works with UpperCaseStrings to NOT take in regular strings as parameters, so
 * we don't accidentally create the above situation
 */
case class UpperCaseString(original: String) {
  val uString = original.toUpperCase

  def originallyUpperCase = uString == original

  override def toString = uString

  override def equals(a: Any) = a match {
    case u: UpperCaseString => u.uString == uString
    case s: String => s == uString
    case _ => false
  }
}

object UpperCaseString {
  implicit def string2UpperCaseString(s: String) = UpperCaseString(s)
  implicit def UppercaseString2String(u: UpperCaseString) = u.toString
}


/** 
 * This class holds data about a solr key, mainly for translating "local" key
 * names to their solr equivalent
 */
case class SolrKey (
  val name: UpperCaseString,
  val valueType: ValueType,
  val isDynamic: Boolean,
  val isMultiValued: Boolean,
  val isSortable: Boolean,
  val aliases: Set[UpperCaseString] = Set()
) {

  if (!name.originallyUpperCase) {
    Logger("SolrKey").warn("SolrKey name %s should be ALL CAPS".format(name.original))
  }
  aliases.map{alias => if (!alias.originallyUpperCase){
    Logger("SolrKey").warn("Alias %s for SolrKey %s should be ALL CAPS".format(name, alias.original))
  }}
  if (isMultiValued && isSortable) {
    Logger("SolrKey").error("Cannot create sortable multivalued key for %s, forcing non-sortable")
  }


  lazy val resolvedName = name + (if(isDynamic) ValueType.postFix(valueType) else "")
  def isAliasOf(alias: UpperCaseString) = aliases(alias)

  def matches(k: UpperCaseString) = (k == name) || isAliasOf(k)

  /**
   * returns true if wildcards should be automatically applied to unquoted values of this key (only relevant for string values)
   */
  def autoWildcard = !(SolrKeyResolver.noAutoWildcardKeys contains name)

  def sortName = name + "_SORT"

  def sortKey: Option[SolrKey] = if (!isMultiValued && isSortable) Some(SolrKey(sortName, String, Static, SingleValued, Sortable)) else None

  def sortify(value: SolrValue): Option[(SolrKey, SolrStringValue)] = sortKey.map{skey => (skey, SolrStringValue(value.sortValue, StrictUnquoted))}

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
  def apply(rawKey: UpperCaseString): Option[SolrKey] = allDocKeys.find{_ matches rawKey} orElse docSpecificKey(rawKey)

  def either(rawkey: UpperCaseString) = apply(rawkey) match {
    case Some(k) => Right(k)
    case None => Left("Unknown key " + rawkey)
  }

  def docSpecificKey(rawKey: UpperCaseString): Option[SolrKey]

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
  val noAutoWildcardKeys: Set[UpperCaseString] = Set("TAG", "IP_ADDRESS", "IPMI_ADDRESS", "MESSAGE")

  val allDocKeys = List(
    SolrKey("DOC_TYPE", String, Static, SingleValued, Sortable),
    SolrKey("LAST_INDEXED", String, Static, SingleValued, Sortable),
    SolrKey("UUID", String, Static, SingleValued, Sortable)
  )


}



/*
 * This is simply a key resolver to use when working with generic documents of
 * any type (for example deleting all out-of-date documents)
 */
object AllDocKeyResolver extends SolrKeyResolver {
  def docSpecificKey(nope: UpperCaseString) = None
}
