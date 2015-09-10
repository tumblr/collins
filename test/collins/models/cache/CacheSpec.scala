package collins.models.cache

import org.specs2.matcher.Expectable
import org.specs2.matcher.Matcher
import org.specs2.mutable

import play.api.test.WithApplication
import play.api.test.FakeApplication

import collins.models.Asset
import collins.models.Asset
import collins.models.AssetMeta
import collins.models.AssetMeta
import collins.models.AssetMetaValue
import collins.models.AssetMetaValue
import collins.models.AssetType
import collins.models.AssetType
import collins.models.IpAddresses
import collins.models.IpAddresses
import collins.models.IpmiInfo
import collins.models.IpmiInfo
import collins.models.MetaWrapper
import collins.models.State
import collins.models.State
import collins.models.Status
import collins.util.IpAddress

/*
 * This specification relies heavily on migrations to populate the database
 */
class CacheSpec extends mutable.Specification {

  "Cache Specification".title

  args(sequential = true)

  // run every test for both guava (in-memory) and hazelcast (distributed)
  val applicationConfigs = List(Map("callbacks.enabled" -> false, "cache.type" -> "in-memory"),
    Map("callbacks.enabled" -> false, "cache.type" -> "distributed", "hazelcast.enabled" -> true))

  applicationConfigs.foreach(c =>
    "Basic cache operations " should {
      "return None when looking for an element not populated in cache " in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val assetFromCache = Cache.get[Asset](Asset.findByTagKey("notincache"))
        assetFromCache mustEqual None
      }
    })

  applicationConfigs.foreach(c =>
    "Caching" should {
      "during find for non existing asset cache should be populated with None" in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val maybeAsset = Asset.findByTag("cacheasset1")
        maybeAsset mustEqual None
        val assetFromCache = Cache.get[Option[Asset]](Asset.findByTagKey("cacheasset1"))
        assetFromCache mustEqual Some(None)
      }

      "after a create asset must be found in cache using tag" in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val assetTag = "cacheasset1"
        val maybeAsset = Asset.findByTag(assetTag)
        maybeAsset mustEqual None
        val assetFromCache = Cache.get[Option[Asset]](Asset.findByTagKey(assetTag))
        assetFromCache mustEqual Some(None)

        val asset = Asset.create(Asset(assetTag, Status.Incomplete.get, AssetType.ServerNode.get))
        val afterCreateMaybeAsset = Asset.findByTag(assetTag)
        afterCreateMaybeAsset mustEqual Some(asset)
        val afterCreateAssetFromCache = Cache.get[Option[Asset]](Asset.findByTagKey(assetTag))
        afterCreateAssetFromCache.get.get must matchAsset(asset)
      }

      "after a create asset must be found in cache using id" in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val assetTag = "cacheasset1"
        val maybeAsset = Asset.findByTag(assetTag)
        maybeAsset mustEqual None
        val assetFromCache = Cache.get[Option[Asset]](Asset.findByTagKey(assetTag))
        assetFromCache mustEqual Some(None)

        val asset = Asset.create(Asset(assetTag, Status.Incomplete.get, AssetType.ServerNode.get))
        val afterCreateMaybeAsset = Asset.findById(asset.id)
        afterCreateMaybeAsset mustEqual Some(asset)
        val afterCreateAssetFromCache = Cache.get[Option[Asset]](Asset.findByIdKey(asset.id))
        afterCreateAssetFromCache.get.get must matchAsset(asset)
      }
    })

  applicationConfigs.foreach(c =>
    "AssetMeta must be cached" in {
      "find pre-populated asset meta" in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val metas = AssetMeta.findAll()
        val metasFromCache = Cache.get[List[AssetMeta]](AssetMeta.findByAllKey)
        metasFromCache.get.zip(metas).foreach { case (s, t) => s must matchAssetMeta(t) }
      }

      "find pre-populated asset meta by name" in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val meta = AssetMeta.findAll().head
        val sameMeta = AssetMeta.findByName(meta.name)
        sameMeta.get must matchAssetMeta(meta)

        val metaFromCache = Cache.get[Option[AssetMeta]](AssetMeta.findByNameKey(meta.name))
        metaFromCache.get.get must matchAssetMeta(meta)
      }

      "find pre-populated asset meta by id" in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val meta = AssetMeta.findAll().head
        val sameMeta = AssetMeta.findById(meta.id)
        sameMeta.get must matchAssetMeta(meta)

        val metaFromCache = Cache.get[Option[AssetMeta]](AssetMeta.findByIdKey(meta.id))
        metaFromCache.get.get must matchAssetMeta(meta)
      }

      "find pre-populated by viewabled " in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val metas = AssetMeta.getViewable()
        val metasFromCache = Cache.get[List[AssetMeta]](AssetMeta.findByViewableKey)
        metasFromCache.get.zip(metas).foreach { case (s, t) => s must matchAssetMeta(t) }
      }
    })

  applicationConfigs.foreach(c =>
    "AssetType must be cached" in {
      "find pre-populated asset types" in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val types = AssetType.find
        val typesFromCache = Cache.get[List[AssetType]](AssetType.findKey)
        typesFromCache.get.zip(types).foreach { case (s, t) => s must matchAssetType(t) }
      }

      "find pre-populated asset type by name" in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val assetType = AssetType.find.head
        val sameType = AssetType.findByName(assetType.name)
        sameType.get must matchAssetType(assetType)

        val typeFromCache = Cache.get[Option[AssetType]](AssetType.findByNameKey(assetType.name))
        typeFromCache.get.get must matchAssetType(assetType)
      }

      "find pre-populated asset type by id" in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val assetType = AssetType.find.head
        val sameType = AssetType.findById(assetType.id)
        sameType.get must matchAssetType(assetType)

        val typeFromCache = Cache.get[Option[AssetType]](AssetType.findByIdKey(assetType.id))
        typeFromCache.get.get must matchAssetType(assetType)
      }
    })

  applicationConfigs.foreach(c =>
    "State must be cached" in {
      "find pre-populated states" in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val states = State.find
        val statesFromCache = Cache.get[List[State]](State.findKey)
        statesFromCache.get.zip(states).foreach {
          case (s, t) =>
            s must matchState(t)
        }
      }

      "find pre-populated state by name" in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val state = State.find.head
        val sameState = State.findByName(state.name)
        sameState.get must matchState(state)

        val stateFromCache = Cache.get[Option[State]](State.findByNameKey(state.name))
        stateFromCache.get.get must matchState(state)
      }

      "find pre-populated state by id" in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val state = State.find.head
        val sameState = State.findById(state.id)
        sameState.get must matchState(state)

        val stateFromCache = Cache.get[Option[State]](State.findByIdKey(state.id))
        stateFromCache.get.get must matchState(state)
      }

      "find pre-populated state by any status" in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val states = State.findByAnyStatus()
        states.size mustEqual 6
        val statesFromCache = Cache.get[List[State]](State.findByAnyStatusKey)
        statesFromCache.get.zip(states).foreach {
          case (s, t) =>
            s must matchState(t)
        }
      }

      "find pre-populated state by status key" in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val status = Status.Maintenance.get
        val state = State.findByStatus(status)
        val stateFromCache = Cache.get[List[State]](State.findByStatusKey(status.id))
        stateFromCache.get.zip(state).foreach { case (s, t) => s must matchState(t) }
      }
    })

  applicationConfigs.foreach(c =>
    "AssetMetaValues must be cached" in {
      "find pre-populated meta value by asset and meta id" in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val asset = Asset.findById(1).get
        val assetMeta = AssetMeta.findById(1).get
        val metaValues = AssetMetaValue.findByAssetAndMeta(asset, assetMeta, 10)
        val metaValuesFromCache = Cache.get[List[MetaWrapper]](AssetMetaValue.findByAssetAndMetaKey(asset.id, assetMeta.id))
        metaValuesFromCache.get.zip(metaValues).foreach {
          case (s, t) => {
            s._value must matchAssetMetaValue(t._value)
            s._meta must matchAssetMeta(t._meta)
          }
        }

        // using method from Asset
        val firstMetaValue = asset.getMetaAttribute(assetMeta.name).get
        firstMetaValue._value must matchAssetMetaValue(metaValuesFromCache.get.head._value)
        firstMetaValue._meta must matchAssetMeta(metaValuesFromCache.get.head._meta)
        firstMetaValue._value must matchAssetMetaValue(metaValues.head._value)
        firstMetaValue._meta must matchAssetMeta(metaValues.head._meta)
      }

      "find pre-populated meta value by asset" in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val asset = Asset.findById(1).get
        val metaValues = AssetMetaValue.findByAsset(asset)
        val metaValuesFromCache = Cache.get[List[MetaWrapper]](AssetMetaValue.findByAssetKey(asset.id))
        metaValuesFromCache.get.zip(metaValues).foreach {
          case (s, t) => {
            s._value must matchAssetMetaValue(t._value)
            s._meta must matchAssetMeta(t._meta)
          }
        }
      }

      "find pre-populated meta value by meta" in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val assetMeta = AssetMeta.findById(1).get
        val metaValues = AssetMetaValue.findByMeta(assetMeta)
        val metaValuesFromCache = Cache.get[List[String]](AssetMetaValue.findByMetaKey(assetMeta.id))
        metaValuesFromCache.get.zip(metaValues).foreach { case (s, t) => s mustEqual t }
      }
    })

  applicationConfigs.foreach(c =>
    "IpmiInfo must be cached" in {

      "find pre-populated ipmi info by asset" in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val asset = Asset.findById(1).get
        val ipmiInfo = IpmiInfo.findByAsset(asset).get
        val ipmiInfoFromCache = Cache.get[Option[IpmiInfo]](IpmiInfo.findByAssetKey(asset.id))
        ipmiInfoFromCache.get.get must matchIpmiInfo(ipmiInfo)
      }

      "find pre-populated all ipmi info by asset" in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val asset = Asset.findById(1).get
        val ipmiInfo = IpmiInfo.findAllByAsset(asset)
        val ipmiInfoFromCache = Cache.get[List[IpmiInfo]](IpmiInfo.findAllByAssetKey(asset.id))
        ipmiInfoFromCache.get.zip(ipmiInfo).foreach {
          case (s, t) =>
            s must matchIpmiInfo(t)
        }
      }

      "find pre-populated ipmi info by id" in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val ipmiInfo = IpmiInfo.get(IpmiInfo(1, "test-user", "", 167772161L, 167772162L, 4294959104L, 1))
        val ipmiInfoFromCache = Cache.get[IpmiInfo](IpmiInfo.findByIdKey(ipmiInfo.id))
        ipmiInfoFromCache.get must matchIpmiInfo(ipmiInfo)
      }
    })

  applicationConfigs.foreach(c =>
    "IpAddress must be cached" in {
      "find ip address by asset" in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val asset = Asset.findById(1).get
        IpAddresses.create(IpAddresses(asset.id, IpAddress.toLong("10.0.0.1"),
          IpAddress.toLong("10.0.0.2"), IpAddress.toLong("255.255.224.0"), "fortesting"))
        val address = IpAddresses.findByAsset(asset).get
        val addressFromCache = Cache.get[Option[IpAddresses]](IpAddresses.findByAssetKey(asset.id))
        addressFromCache.get.get must matchAddress(address)
      }

      "find all ip address by asset" in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val asset = Asset.findById(1).get
        IpAddresses.create(IpAddresses(asset.id, IpAddress.toLong("10.0.0.1"),
          IpAddress.toLong("10.0.0.2"), IpAddress.toLong("255.255.224.0"), "fortesting"))
        val addresses = IpAddresses.findAllByAsset(asset)
        val addressesFromCache = Cache.get[List[IpAddresses]](IpAddresses.findAllByAssetKey(asset.id))
        addressesFromCache.get.zip(addresses).foreach { case (s, t) => s must matchAddress(t) }
      }

      "find ip address by id" in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val asset = Asset.findById(1).get
        val address = IpAddresses.get(IpAddresses.create(new IpAddresses(asset.id, IpAddress.toLong("10.0.0.1"),
          IpAddress.toLong("10.0.0.2"), IpAddress.toLong("255.255.224.0"), "fortesting")))
        val addressFromCache = Cache.get[IpAddresses](IpAddresses.findByIdKey(address.id))
        addressFromCache.get must matchAddress(address)
      }

      "find pools in use" in new WithApplication(FakeApplication(
        additionalConfiguration = c)) {
        val asset = Asset.findById(1).get
        IpAddresses.create(new IpAddresses(asset.id, IpAddress.toLong("10.0.0.1"),
          IpAddress.toLong("10.0.0.2"), IpAddress.toLong("255.255.224.0"), "fortesting"))
        val pools = IpAddresses.getPoolsInUse()
        pools mustEqual Set("fortesting")
        val poolsFromCache = Cache.get[Option[IpAddresses]](IpAddresses.findPoolsInUseKey)
        poolsFromCache mustEqual Some(pools)
      }
    })
}

case class matchAsset(t: Asset) extends Matcher[Asset] {
  def apply[S <: Asset](s: Expectable[S]) = {
    val o = s.value
    val r = t.tag == o.tag && t.statusId == o.statusId && t.assetTypeId == o.assetTypeId && t.created == o.created &&
      t.updated == o.updated && t.deleted == o.deleted && t.id == o.id && t.stateId == o.stateId
    result(r, "Asset matches expectation : " + s.description, "Asset did not match expectation: " + s.description, s)
  }
}

case class matchAddress(t: IpAddresses) extends Matcher[IpAddresses] {
  def apply[S <: IpAddresses](s: Expectable[S]) = {
    val o = s.value
    val r = t.assetId == o.assetId && t.netmask == o.netmask && t.gateway == o.gateway && t.pool == o.pool && t.id == o.id
    result(r, "Ipaddresses matches expectation : " + s.description, "IpAddresses did not match expectation: " + s.description, s)
  }
}

case class matchIpmiInfo(t: IpmiInfo) extends Matcher[IpmiInfo] {
  def apply[S <: IpmiInfo](s: Expectable[S]) = {
    val o = s.value
    val r = t.assetId == o.assetId && t.username == o.username &&
      t.password == o.password && t.gateway == o.gateway &&
      t.address == o.address && t.netmask == o.netmask && t.id == o.id
    result(r, "IpmiInfo matches expectation : " + s.description, "IpmiInfo did not match expectation: " + s.description, s)
  }
}

case class matchAssetMetaValue(t: AssetMetaValue) extends Matcher[AssetMetaValue] {
  def apply[S <: AssetMetaValue](s: Expectable[S]) = {
    val o = s.value
    val r = t.assetId == o.assetId && t.assetMetaId == o.assetMetaId && t.groupId == o.groupId && t.value == o.value
    result(r, "AssetMetaValue matches expectation : " + s.description, "AssetMetaValue did not match expectation: " + s.description, s)
  }
}

case class matchAssetMeta(t: AssetMeta) extends Matcher[AssetMeta] {
  def apply[S <: AssetMeta](s: Expectable[S]) = {
    val o = s.value
    val r = t.name == o.name && t.priority == o.priority && t.label == o.label && t.description == o.description && t.id == o.id && t.value_type == o.value_type
    result(r, "AssetMeta matches expectation : " + s.description, "AssetMeta did not match expectation: " + s.description, s)
  }
}

case class matchState(t: State) extends Matcher[State] {
  def apply[S <: State](s: Expectable[S]) = {
    val o = s.value
    val r = t.id == o.id && t.status == o.status && t.name == o.name && t.label == o.label && t.description == o.description
    result(r, "State matches expectation : " + s.description, "State did not match expectation: " + s.description, s)
  }
}

case class matchAssetType(t: AssetType) extends Matcher[AssetType] {
  def apply[S <: AssetType](s: Expectable[S]) = {
    val o = s.value
    val r = t.name == o.name && t.label == o.label && t.id == o.id
    result(r, "AssetType matches expectation : " + s.description, "AssetType did not match expectation: " + s.description, s)
  }
}