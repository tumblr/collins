package collins.solr

import models.{Asset, AssetFinder, AssetMeta, AssetMetaValue, AssetType, IpAddresses, MetaWrapper, Page, PageParams, State, Status, Truthy}
import models.asset.AssetView
import models.IpmiInfo.Enum._


import Solr.AssetSolrDocument
import AssetMeta.ValueType
import AssetMeta.ValueType._


/** 
 * This class holds data about a solr key, mainly for translating "local" key
 * names to their solr equivalent
 */
case class SolrKey (
  val name: String,
  val valueType: ValueType,
  val isDynamic: Boolean = true
) {
  lazy val resolvedName = name.toUpperCase + (if(isDynamic) ValueType.postFix(valueType) else "")
  def isAliasOf(alias: String) = false //override for aliases

  def matches(k: String) = (k == name) || isAliasOf(k)
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
