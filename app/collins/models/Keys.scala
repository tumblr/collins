package collins.models

import org.squeryl.Schema

import collins.models.shared.Keys
import collins.models.shared.BasicModel
import collins.models.shared.AnormAdapter
import collins.models.shared.IpAddressable
import collins.models.shared.IpAddressStorage

trait StateKeys extends Keys[State] { self: AnormAdapter[State] =>

  val findKey = "State.find"
  def findByIdKey(id: Int) = f"State.findById(${id}%d)"
  def findByNameKey(name: String) = f"State.findByName(${name.toLowerCase}%s)"
  val findByAnyStatusKey = "State.findByAnyStatus"
  def findByStatusKey(status: Int) = f"State.findByStatus(${status}%d)"

  override def cacheKeys(s: State) = Seq(
    findKey,
    findByIdKey(s.id),
    findByNameKey(s.name),
    findByAnyStatusKey,
    findByStatusKey(s.status))
}

trait AssetLogKeys extends Keys[AssetLog] { self: AnormAdapter[AssetLog] =>
  def cacheKeys(l: AssetLog) = Seq()
}

trait AssetMetaValueKeys extends Keys[AssetMetaValue] { self: BasicModel[AssetMetaValue] =>

  def findByMetaKey(id: Long) = f"AssetMetaValue.findByMeta(${id}%d)"
  def findByAssetKey(id: Long) = f"AssetMetaValue.findByAsset(${id}%d)"
  def findByAssetAndMetaKey(assetId: Long, metaId: Long) = f"AssetMetaValue.findByAssetAndMeta(${assetId}%d, ${metaId}%d)"

  def cacheKeys(amv: AssetMetaValue) = Seq(
    findByMetaKey(amv.assetMetaId),
    findByAssetKey(amv.assetId),
    findByAssetAndMetaKey(amv.assetId, amv.assetMetaId))
}

trait IpAddressKeys[T <: IpAddressable] extends Keys[T] { self: IpAddressStorage[T] =>

  def findAllByAssetKey(id: Long) = f"${storageName}%s.findAllByAsset(${id}%d)"
  def findByAssetKey(id: Long) = f"${storageName}%s.findByAsset(${id}%d)"
  def findByIdKey(id: Long) = f"${storageName}%s.get(${id}%d)"
  val findPoolsInUseKey = "getPoolsInUse"

  override def cacheKeys(t: T) = {
    Seq(
      findAllByAssetKey(t.assetId),
      findByAssetKey(t.assetId),
      findByIdKey(t.id),
      findPoolsInUseKey)
  }
}

trait StatusKeys extends Keys[Status] { self: AnormAdapter[Status] =>
  val findKey = "Status.find"
  def findByIdKey(id: Int) = f"Status.findById(${id}%d)"
  def findByNameKey(name: String) = f"Status.findByName(${name.toLowerCase}%s)"

  override def cacheKeys(s: Status) = Seq(
    findKey,
    findByIdKey(s.id),
    findByNameKey(s.name))
}

trait AssetKeys extends Keys[Asset] { self: AnormAdapter[Asset] =>

  def findByTagKey(tag: String) = f"Asset.findByTag(${tag.toLowerCase}%s)"
  def findByIdKey(id: Long) = f"Asset.findById(${id}%d)"

  override def cacheKeys(asset: Asset) = Seq(
    findByTagKey(asset.tag),
    findByIdKey(asset.id))
}

trait AssetMetaKeys extends Keys[AssetMeta] { self: AnormAdapter[AssetMeta] =>

  val findByAllKey = "AssetMeta.findAll"
  val findByViewableKey = "AssetMeta.getViewable"
  def findByIdKey(id: Long) = f"AssetMeta.findById(${id}%d)"
  def findByNameKey(name: String) = f"AssetMeta.findByName(${name.toUpperCase}%s)"

  override def cacheKeys(a: AssetMeta) = Seq(
    findByNameKey(a.name),
    findByIdKey(a.id),
    findByAllKey,
    findByViewableKey)
}

trait AssetTypeKeys extends Keys[AssetType] { self: AnormAdapter[AssetType] =>

  val findKey = "AssetType.find"
  def findByNameKey(name: String) = f"AssetType.findByName(${name.toUpperCase}%s)"
  def findByIdKey(id: Int) = f"AssetType.findById(${id}%d)"

  override def cacheKeys(a: AssetType) = Seq(
    findByIdKey(a.id),
    findByNameKey(a.name),
    findKey)
}