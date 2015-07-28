package collins.models.cache

import org.specs2.mutable
import play.api.test.WithApplication

import collins.util.IpAddress

import collins.models.Asset
import collins.models.AssetMeta
import collins.models.AssetMetaValue
import collins.models.AssetType
import collins.models.IpmiInfo
import collins.models.State
import collins.models.Status
import collins.models.IpAddresses

/*
 * This specification relies heavily on migrations to populate the database
 */
class CacheSpec extends mutable.Specification {

  "Cache Specification".title

  args(sequential = true)

  "Basic cache operations " should {
    "return None when looking for an element not populated in cache " in new WithApplication {
      val assetFromCache = Cache.get[Option[Asset]](Asset.findByTagKey("notincache"))
      assetFromCache mustEqual None
    }
  }

  "Assets must be cached" in {

    "during find for non existing asset cache should be populated with None" in new WithApplication {
      val maybeAsset = Asset.findByTag("cacheasset1")
      maybeAsset mustEqual None
      val assetFromCache = Cache.get[Option[Asset]](Asset.findByTagKey("cacheasset1"))
      assetFromCache mustEqual Some(None)
    }

    "after a create asset must be found in cache using tag" in new WithApplication {
      val assetTag = "cacheasset1"
      val maybeAsset = Asset.findByTag(assetTag)
      maybeAsset mustEqual None
      val assetFromCache = Cache.get[Option[Asset]](Asset.findByTagKey(assetTag))
      assetFromCache mustEqual Some(None)

      val asset = Asset.create(Asset(assetTag, Status.Incomplete.get, AssetType.ServerNode.get))
      val afterCreateMaybeAsset = Asset.findByTag(assetTag)
      afterCreateMaybeAsset mustEqual Some(asset)
      val afterCreateAssetFromCache = Cache.get[Option[Asset]](Asset.findByTagKey(assetTag))
      afterCreateAssetFromCache mustEqual Some(Some(asset))
    }

    "after a create asset must be found in cache using id" in new WithApplication {
      val assetTag = "cacheasset1"
      val maybeAsset = Asset.findByTag(assetTag)
      maybeAsset mustEqual None
      val assetFromCache = Cache.get[Option[Asset]](Asset.findByTagKey(assetTag))
      assetFromCache mustEqual Some(None)

      val asset = Asset.create(Asset(assetTag, Status.Incomplete.get, AssetType.ServerNode.get))
      val afterCreateMaybeAsset = Asset.findById(asset.id)
      afterCreateMaybeAsset mustEqual Some(asset)
      val afterCreateAssetFromCache = Cache.get[Option[Asset]](Asset.findByIdKey(asset.id))
      afterCreateAssetFromCache mustEqual Some(Some(asset))
    }
  }

  "AssetMeta must be cached" in {
    "find pre-populated asset meta" in new WithApplication {
      val metas = AssetMeta.findAll()
      val metasFromCache = Cache.get[List[AssetMeta]](AssetMeta.findByAllKey)
      metasFromCache mustEqual Some(metas)
    }

    "find pre-populated asset meta by name" in new WithApplication {
      val meta = AssetMeta.findAll().head
      val sameMeta = AssetMeta.findByName(meta.name)
      sameMeta mustEqual Some(meta)

      val metaFromCache = Cache.get[Option[AssetMeta]](AssetMeta.findByNameKey(meta.name))
      metaFromCache mustEqual Some(Some(meta))
    }

    "find pre-populated asset meta by id" in new WithApplication {
      val meta = AssetMeta.findAll().head
      val sameMeta = AssetMeta.findById(meta.id)
      sameMeta mustEqual Some(meta)

      val metaFromCache = Cache.get[Option[AssetMeta]](AssetMeta.findByIdKey(meta.id))
      metaFromCache mustEqual Some(Some(meta))
    }

    "find pre-populated by viewabled " in new WithApplication {
      val metas = AssetMeta.getViewable()
      val metasFromCache = Cache.get[List[AssetMeta]](AssetMeta.findByViewableKey)
      metasFromCache mustEqual Some(metas)
    }
  }

  "AssetType must be cached" in {
    "find pre-populated asset types" in new WithApplication {
      val types = AssetType.find
      val typesFromCache = Cache.get[Option[List[AssetType]]](AssetType.findKey)
      typesFromCache mustEqual Some(types)
    }

    "find pre-populated asset type by name" in new WithApplication {
      val assetType = AssetType.find.head
      val sameType = AssetType.findByName(assetType.name)
      sameType mustEqual Some(assetType)

      val typeFromCache = Cache.get[Option[AssetType]](AssetType.findByNameKey(assetType.name))
      typeFromCache mustEqual Some(Some(assetType))
    }

    "find pre-populated asset type by id" in new WithApplication {
      val assetType = AssetType.find.head
      val sameType = AssetType.findById(assetType.id)
      sameType mustEqual Some(assetType)

      val typeFromCache = Cache.get[Option[AssetType]](AssetType.findByIdKey(assetType.id))
      typeFromCache mustEqual Some(Some(assetType))
    }
  }

  "State must be cached" in {
    "find pre-populated states" in new WithApplication {
      val states = State.find
      val statesFromCache = Cache.get[List[State]](State.findKey)
      statesFromCache mustEqual Some(states)
    }

    "find pre-populated state by name" in new WithApplication {
      val state = State.find.head
      val sameState = State.findByName(state.name)
      sameState mustEqual Some(state)

      val stateFromCache = Cache.get[Option[State]](State.findByNameKey(state.name))
      stateFromCache mustEqual Some(Some(state))
    }

    "find pre-populated state by id" in new WithApplication {
      val state = State.find.head
      val sameState = State.findById(state.id)
      sameState mustEqual Some(state)

      val stateFromCache = Cache.get[Option[State]](State.findByIdKey(state.id))
      stateFromCache mustEqual Some(Some(state))
    }

    "find pre-populated state by any status" in new WithApplication {
      val states = State.findByAnyStatus()
      states.size mustEqual 6
      val statesFromCache = Cache.get[List[State]](State.findByAnyStatusKey)
      statesFromCache mustEqual Some(states)
    }

    "find pre-populated state by status key" in new WithApplication {
      val status = Status.Maintenance.get
      val state = State.findByStatus(status)
      val stateFromCache = Cache.get[Option[State]](State.findByStatusKey(status.id))
      stateFromCache mustEqual Some(state)
    }
  }

  "AssetMetaValues must be cached" in {
    "find pre-populated meta value by asset and meta id" in new WithApplication {
      val asset = Asset.findById(1).get
      val assetMeta = AssetMeta.findById(1).get
      val metaValues = AssetMetaValue.findByAssetAndMeta(asset, assetMeta, 10)
      val metaValuesFromCache = Cache.get[List[AssetMetaValue]](AssetMetaValue.findByAssetAndMetaKey(asset.id, assetMeta.id))
      metaValuesFromCache mustEqual Some(metaValues)

      // using method from Asset
      val firstMetaValue = asset.getMetaAttribute(assetMeta.name)
      firstMetaValue mustEqual metaValuesFromCache.get.headOption
      firstMetaValue mustEqual metaValues.headOption
    }

    "find pre-populated meta value by asset" in new WithApplication {
      val asset = Asset.findById(1).get
      val metaValues = AssetMetaValue.findByAsset(asset)
      val metaValuesFromCache = Cache.get[List[AssetMetaValue]](AssetMetaValue.findByAssetKey(asset.id))
      metaValuesFromCache mustEqual Some(metaValues)
    }

    "find pre-populated meta value by meta" in new WithApplication {
      val assetMeta = AssetMeta.findById(1).get
      val metaValues = AssetMetaValue.findByMeta(assetMeta)
      val metaValuesFromCache = Cache.get[List[AssetMetaValue]](AssetMetaValue.findByMetaKey(assetMeta.id))
      metaValuesFromCache mustEqual Some(metaValues)
    }
  }

  "IpmiInfo must be cached" in {

    "find pre-populated ipmi info by asset" in new WithApplication {
      val asset = Asset.findById(1).get
      val ipmiInfo = IpmiInfo.findByAsset(asset)
      val ipmiInfoFromCache = Cache.get[Option[IpmiInfo]](IpmiInfo.findByAssetKey(asset.id))
      ipmiInfoFromCache mustEqual Some(ipmiInfo)
    }

    "find pre-populated all ipmi info by asset" in new WithApplication {
      val asset = Asset.findById(1).get
      val ipmiInfo = IpmiInfo.findAllByAsset(asset)
      val ipmiInfoFromCache = Cache.get[List[IpmiInfo]](IpmiInfo.findAllByAssetKey(asset.id))
      ipmiInfoFromCache mustEqual Some(ipmiInfo)
    }

    "find pre-populated ipmi info by id" in new WithApplication {
      val ipmiInfo = IpmiInfo.get(IpmiInfo(1, "test-user", "", 167772161L, 167772162L, 4294959104L, 1))
      val ipmiInfoFromCache = Cache.get[Option[IpmiInfo]](IpmiInfo.findByIdKey(ipmiInfo.id))
      ipmiInfoFromCache mustEqual Some(ipmiInfo)
    }
  }

  "IpAddress must be cached" in {
    "find ip address by asset" in new WithApplication {
      val asset = Asset.findById(1).get
      IpAddresses.create(IpAddresses(asset.id, IpAddress.toLong("10.0.0.1"),
        IpAddress.toLong("10.0.0.2"), IpAddress.toLong("255.255.224.0"), "fortesting"))
      val address = IpAddresses.findByAsset(asset)
      val addressFromCache = Cache.get[Option[IpAddresses]](IpAddresses.findByAssetKey(asset.id))
      addressFromCache mustEqual Some(address)
    }

    "find all ip address by asset" in new WithApplication {
      val asset = Asset.findById(1).get
      IpAddresses.create(IpAddresses(asset.id, IpAddress.toLong("10.0.0.1"),
        IpAddress.toLong("10.0.0.2"), IpAddress.toLong("255.255.224.0"), "fortesting"))
      val addresses = IpAddresses.findAllByAsset(asset)
      val addressesFromCache = Cache.get[Option[List[IpAddresses]]](IpAddresses.findAllByAssetKey(asset.id))
      addressesFromCache mustEqual Some(addresses)
    }

    "find ip address by id" in new WithApplication {
      val asset = Asset.findById(1).get
      val address = IpAddresses.get(IpAddresses.create(IpAddresses(asset.id, IpAddress.toLong("10.0.0.1"),
        IpAddress.toLong("10.0.0.2"), IpAddress.toLong("255.255.224.0"), "fortesting")))
      val addressFromCache = Cache.get[Option[IpAddresses]](IpAddresses.findByIdKey(address.id))
      addressFromCache mustEqual Some(address)
    }

    "find pools in use" in new WithApplication {
      val asset = Asset.findById(1).get
      IpAddresses.create(IpAddresses(asset.id, IpAddress.toLong("10.0.0.1"),
        IpAddress.toLong("10.0.0.2"), IpAddress.toLong("255.255.224.0"), "fortesting"))
      val pools = IpAddresses.getPoolsInUse()
      pools mustEqual Set("fortesting")
      val poolsFromCache = Cache.get[Option[IpAddresses]](IpAddresses.findPoolsInUseKey)
      poolsFromCache mustEqual Some(pools)
    }
  }
}