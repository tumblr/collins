package collins.solr

import models.{Asset, AssetFinder, AssetMeta, AssetMetaValue, AssetType, IpAddresses, MetaWrapper, Page, PageParams, State, Status, Truthy}
import models.asset.AssetView
import models.IpmiInfo.Enum._


import Solr.AssetSolrDocument
import AssetMeta.ValueType
import AssetMeta.ValueType._


sealed trait SolrKey {
  def name: String
  def valueType: ValueType
  
  def resolvedName: String
}



/** 
 * This class holds data about a solr key, mainly for translating "local" key
 * names to their solr equivalent
 */
case class SolrValueKey (
  val name: String,
  val valueType: ValueType,
  val isDynamic: Boolean = true
) extends SolrKey {
  lazy val resolvedName = name.toUpperCase + (if(isDynamic) ValueType.postFix(valueType) else "")
  def isAliasOf(alias: String) = false //override for aliases

  def matches(k: String) = (k == name) || isAliasOf(k)

  /**
   * returns true if wildcards should be automatically applied to unquoted values of this key (only relevant for string values)
   */
  def autoWildcard = !(SolrKeyResolver.noAutoWildcardKeys contains name)

  lazy val sortKey = SolrSortKey(this)

  /**
   * convert a value into a form that can be sorted
   *
   * This is overridden by several keys, such as type and status, to replace the enum id with the string value
   */
  def sortify(value: SolrValue): SolrStringValue = SolrStringValue(value.solrValue)

}

/**
 * Why we need Sort keys:
 * - cannot sort on multi-valued fields
 * - need to convert enums to string representation
 * - solr doesn't have dynamic copyfields
*/
case class SolrSortKey(val source: SolrValueKey) extends SolrKey {
  val valueType = String
  val name = source.name

  lazy val resolvedName = name.toUpperCase + "_sort"
}

object SolrKey {
  def apply(name: String, valueType: ValueType, isDyn: Boolean = true): SolrValueKey = 
    SolrValueKey(name, valueType, isDyn)
  
}

/**
 * Mixin for enum keys, allows us to resolve the solr key and then validate
 * the enum value by passing it to the valueLookup method.
 */
trait EnumKey{ self: SolrKey =>
  def lookupByName(value: String): Option[SolrEnumValue]
  def lookupById(value: Int): Option[SolrEnumValue]
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

  /**
   * each key is an "incoming" field from a query, the ValueType is the
   * expected type of the key, and the Boolean indicates whether the key in
   * Solr is static(false) or dynamic(true)
   *
   * NOTE - For now, any single-valued field that needs to be sortable has to be explicitly declared
   */
  lazy val nonMetaKeys: Seq[SolrValueKey] = List(
    SolrKey("TAG", String,false), 
    SolrKey("CREATED", String,false), 
    SolrKey("UPDATED", String,false), 
    SolrKey("DELETED", String,false),
    SolrKey("IP_ADDRESS", String,false),
    SolrKey("PRIMARY_ROLE", String,false),
    SolrKey("HOSTNAME", String,false),
    SolrKey(IpmiAddress.toString, String, true),
    SolrKey(IpmiUsername.toString, String, true),
    SolrKey(IpmiPassword.toString, String, true),
    SolrKey(IpmiGateway.toString, String, true),
    SolrKey(IpmiNetmask.toString, String, true)
  ) ++ Solr.plugin.map{_.serializer.generatedFields}.getOrElse(List())

  val typeKey = new SolrValueKey("TYPE",Integer,false) with EnumKey {
    def lookupByName(value: String) = AssetType.findByName(value.toUpperCase).map(_.name)
    def lookupById(value: String) = AssetType.findById(value.toUpperCase).map(_.name)
    override def isAliasOf(a: String) = a == "ASSETTYPE"

  }

  val statusKey = new SolrValueKey("STATUS",Integer,false) with EnumKey {
    def lookupByName(value: String) = Status.findByName(value).map{_.name}
    def lookupById(value: String) = Status.findById(value).map{_.name}
  }

  val stateKey = new SolrValueKey("STATE", Integer, false) with EnumKey {
    def lookupByName(value: String) = State.findByName(value).map{_.name}
    def lookupById(value: String) = State.findById(value).map{_.name}
  }

  val enumKeys = typeKey :: statusKey :: stateKey :: Nil

  def apply(_rawkey: String): Option[SolrValueKey] = {
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
