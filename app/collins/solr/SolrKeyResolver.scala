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
  val isSortable: Boolean
) {
  require(!(isMultiValued && isSortable), "Cannot create sortable multivalue keys (yet)")

  lazy val resolvedName = name.toUpperCase + (if(isDynamic) ValueType.postFix(valueType) else "")
  def isAliasOf(alias: String) = false //override for aliases

  def matches(k: String) = (k == name) || isAliasOf(k)

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
  val noAutoWildcardKeys = List("TAG", "IP_ADDRESS", "IPMI_ADDRESS")

  val allDocKeys = List(
    SolrKey("DOC_TYPE", String, Static, SingleValued, Sortable),
    SolrKey("LAST_INDEXED", String, Static, SingleValued, Sortable),
    SolrKey("UUID", String, Static, SingleValued, Sortable)
  )

  /**
   * each key is an "incoming" field from a query, the ValueType is the
   * expected type of the key, and the Boolean indicates whether the key in
   * Solr is static(false) or dynamic(true)
   *
   * NOTE - For now, any single-valued field that needs to be sortable has to be explicitly declared
   */
  lazy val nonMetaKeys: Seq[SolrKey] = List(
    SolrKey("ID", Integer, Static, SingleValued, Sortable),
    SolrKey("TAG", String, Static, SingleValued, Sortable), 
    SolrKey("CREATED", String, Static, SingleValued, Sortable), 
    SolrKey("UPDATED", String, Static, SingleValued, Sortable), 
    SolrKey("DELETED", String, Static, SingleValued, Sortable),
    SolrKey("IP_ADDRESS", String, Static, MultiValued, NotSortable),
    SolrKey("PRIMARY_ROLE", String, Static, SingleValued, Sortable),
    SolrKey("HOSTNAME", String, Static, SingleValued, Sortable),
    SolrKey(IpmiAddress.toString, String, Dynamic, SingleValued, Sortable),
    SolrKey(IpmiUsername.toString, String, Dynamic, SingleValued, Sortable),
    SolrKey(IpmiPassword.toString, String, Dynamic, SingleValued, Sortable),
    SolrKey(IpmiGateway.toString, String, Dynamic, SingleValued, Sortable),
    SolrKey(IpmiNetmask.toString, String, Dynamic, SingleValued, Sortable)
  ) ++ Solr.plugin.map{_.serializer.generatedFields}.getOrElse(List()) ++ allDocKeys

  val typeKey = new SolrKey("TYPE",String,Static, SingleValued, Sortable) with EnumKey {
    def lookupByName(value: String) = AssetType.findByName(value.toUpperCase).map(_.name)
    def lookupById(value: Int) = AssetType.findById(value).map(_.name)
    override def isAliasOf(a: String) = a == "ASSETTYPE"

  }

  val statusKey = new SolrKey("STATUS", String, Static, SingleValued, Sortable) with EnumKey {
    def lookupByName(value: String) = Status.findByName(value).map{_.name}
    def lookupById(value: Int) = Status.findById(value).map{_.name}
  }

  val stateKey = new SolrKey("STATE", String, Static, SingleValued, Sortable) with EnumKey {
    def lookupByName(value: String) = State.findByName(value).map{_.name}
    def lookupById(value: Int) = State.findById(value).map{_.name}
  }

  val enumKeys = typeKey :: statusKey :: stateKey :: Nil

  def apply(_rawkey: String): Option[SolrKey] = {
    val ukey = _rawkey.toUpperCase
    nonMetaKeys.find(_ matches ukey)
      .orElse(enumKeys.find(_ matches ukey))
      .orElse(AssetMeta.findByName(ukey).map{_.getSolrKey})
  }

  def either(_rawkey: String) = apply(_rawkey) match {
    case Some(k) => Right(k)
    case None => Left("Unknown key " + _rawkey)
  }

}
