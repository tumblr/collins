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
  val isDynamic: Boolean = true,
  val isMultiValued: Boolean = false
) extends SolrKey {
  lazy val resolvedName = name.toUpperCase + (if(isDynamic) ValueType.postFix(valueType) else "")
  def isAliasOf(alias: String) = false //override for aliases

  def matches(k: String) = (k == name) || isAliasOf(k)

  /**
   * returns true if wildcards should be automatically applied to unquoted values of this key (only relevant for string values)
   */
  def autoWildcard = !(SolrKeyResolver.noAutoWildcardKeys contains name)

  /**
   * Only single-valued keys can be used for sorting
   */
  lazy val sortKey = Some(SolrSortKey(this)).filter(!isMultiValued)

  /**
   * override for custom sort key generation
   *
   */
  def sortify(value: SolrSingleValue): SolrStringValue = SolrStringValue(value.value.toString)

  def sortTuple(value: SolrValue): Option[(SolrKey, SolrValue)] = value match {
    case s: SolrSingleValue => sortKey.map{k => (k,sortify(s))}
    case _ => throw new IllegalArgumentException("Cannot sortify non-single values")
  }
}

case class SolrSortKey(val source: SolrValueKey) extends SolrKey {
  val valueType = String
  val name = source.name

  lazy val resolvedName = name.toUpperCase + "_sort"
}

object SolrKey {
  def apply(name: String, valueType: ValueType, isDyn: Boolean = true, multi: Boolean = false): SolrValueKey = 
    SolrValueKey(name, valueType, isDyn, multi)
  
}

/**
 * Mixin for enum keys, allows us to resolve the solr key and then validate
 * the enum value by passing it to the valueLookup method.
 */
trait KeyLookup{ self: SolrKey =>
  def lookupValue(value: String): Option[Int]
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
  lazy val nonMetaKeys: Seq[SolrKey] = List(
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

  val typeKey = new SolrKey("TYPE",Integer,false) with KeyLookup {
    def lookupValue(value: String) = AssetType.findByName(value.toUpperCase).map(_.id)
    override def isAliasOf(a: String) = a == "ASSETTYPE"
  }

  val statusKey = new SolrKey("STATUS",Integer,false) with KeyLookup {
    def lookupValue(value: String) = Status.findByName(value).map{_.id}
  }

  val stateKey = new SolrKey("STATE", Integer, false) with KeyLookup {
    def lookupValue(value: String) = State.findByName(value).map{_.id}
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
