package collins.models

import play.api.libs.json.Json
import play.api.libs.json.JsObject
import play.api.libs.json.JsNumber
import play.api.libs.json.JsString

import org.squeryl.PrimitiveTypeMode._
import org.squeryl.Schema
import org.squeryl.Table

import collins.solr.SolrKey
import collins.solr.SolrSingleValue
import collins.solr.SolrIntValue
import collins.solr.SolrDoubleValue
import collins.solr.SolrBooleanValue
import collins.solr.SolrStringValue

import collins.models.cache.Cache
import collins.models.shared.ValidatedEntity
import collins.models.shared.AnormAdapter

import collins.callbacks.CallbackDatum

case class AssetMeta(
    name: String,
    priority: Int,
    label: String,
    description: String,
    id: Long = 0,
    value_type: Int = AssetMeta.ValueType.String.id) extends ValidatedEntity[Long] with CallbackDatum {
  override def validate() {
    require(name != null && name.toUpperCase == name && name.size > 0, "Name must be all upper case, length > 0")
    require(AssetMeta.isValidName(name), "Name must be all upper case, alpha numeric (and hyphens): %s".format(name))
    require(description != null && description.length > 0, "Need a description")
    require(AssetMeta.ValueType.valIds(value_type), "Invalid value_type, must be one of [%s]".format(AssetMeta.ValueType.valStrings.mkString(",")))
  }
  override def asJson: String = {
    Json.stringify(JsObject(Seq(
      "ID" -> JsNumber(id),
      "NAME" -> JsString(name),
      "PRIORITY" -> JsNumber(priority),
      "LABEL" -> JsString(label),
      "DESCRIPTION" -> JsString(description))))
  }

  override def compare(z: Any): Boolean = {
    if (z == null)
      return false
    val ar = z.asInstanceOf[AnyRef]
    if (!ar.getClass.isAssignableFrom(this.getClass))
      false
    else {
      val other = ar.asInstanceOf[AssetMeta]
      this.name == other.name && this.priority == other.priority && this.label == other.label &&
        this.description == other.description && this.value_type == other.value_type
    }
  }

  def getValueType(): AssetMeta.ValueType = AssetMeta.ValueType(value_type)

  def valueType = getValueType

  def getSolrKey(): SolrKey = SolrKey(name, valueType, true, true, false)

  def validateValue(value: String): Boolean = typeStringValue(value).isDefined

  def typeStringValue(value: String): Option[SolrSingleValue] = getValueType() match {
    case AssetMeta.ValueType.Integer => try {
      Some(SolrIntValue(Integer.parseInt(value)))
    } catch {
      case _: Throwable => None
    }
    case AssetMeta.ValueType.Boolean => try {
      Some(SolrBooleanValue((new Truthy(value)).isTruthy))
    } catch {
      case _: Throwable => None
    }
    case AssetMeta.ValueType.Double => try {
      Some(SolrDoubleValue(java.lang.Double.parseDouble(value)))
    } catch {
      case _: Throwable => None
    }
    case _ => Some(SolrStringValue(value))
  }
}

object AssetMeta extends Schema with AnormAdapter[AssetMeta] with AssetMetaKeys {
  private[this] val NameR = """[A-Za-z0-9\-_]+""".r.pattern.matcher(_)

  override val tableDef = table[AssetMeta]("asset_meta")
  on(tableDef)(a => declare(
    a.id is (autoIncremented, primaryKey),
    a.name is (unique),
    a.priority is (indexed)))

  override def delete(a: AssetMeta): Int = inTransaction {
    afterDeleteCallback(a) {
      tableDef.deleteWhere(p => p.id === a.id)
    }
  }

  def isValidName(name: String): Boolean = {
    name != null && name.nonEmpty && NameR(name).matches
  }

  def findAll(): List[AssetMeta] = Cache.get(findByAllKey, inTransaction {
    from(tableDef)(s => select(s)).toList
  })

  def findById(id: Long) = Cache.get(findByIdKey(id), inTransaction {
    tableDef.lookup(id)
  })

  def findOrCreateFromName(name: String, valueType: ValueType = ValueType.String): AssetMeta = findByName(name).getOrElse {
    create(AssetMeta(
      name = name.toUpperCase,
      priority = -1,
      label = name.toLowerCase.capitalize,
      description = name,
      value_type = valueType.id))
    findByName(name).get
  }

  override def get(a: AssetMeta) = findById(a.id).get

  def findByName(name: String): Option[AssetMeta] = Cache.get(findByNameKey(name), inTransaction {
    tableDef.where(a =>
      a.name.toUpperCase === name.toUpperCase).headOption
  })

  def getViewable(): List[AssetMeta] = Cache.get(findByViewableKey, inTransaction {
    from(tableDef)(a =>
      where(a.priority gt -1)
        select (a)
        orderBy (a.priority asc)).toList
  })

  type ValueType = ValueType.Value
  object ValueType extends Enumeration {
    val String = Value(1, "STRING")
    val Integer = Value(2, "INTEGER")
    val Double = Value(3, "DOUBLE")
    val Boolean = Value(4, "BOOLEAN")

    def valStrings = values.map { _.toString }
    def valIds = values.map { _.id }

    val postFix = Map[ValueType, String](
      String -> "_meta_s",
      Integer -> "_meta_i",
      Double -> "_meta_d",
      Boolean -> "_meta_b")
  }

  // DO NOT ADD ANYTHING TO THIS
  // DEPRECATED
  type Enum = Enum.Value
  object Enum extends Enumeration(1) {
    val ServiceTag = Value(1, "SERVICE_TAG")
    val ChassisTag = Value(2, "CHASSIS_TAG")
    val RackPosition = Value(3, "RACK_POSITION")
    val PowerPort = Value(4, "POWER_PORT")
    //val SwitchPort = Value(5, "SWITCH_PORT") Deprecated by id LldpPortIdValue

    val CpuCount = Value(6, "CPU_COUNT")
    val CpuCores = Value(7, "CPU_CORES")
    val CpuThreads = Value(8, "CPU_THREADS")
    val CpuSpeedGhz = Value(9, "CPU_SPEED_GHZ")
    val CpuDescription = Value(10, "CPU_DESCRIPTION")

    val MemorySizeBytes = Value(11, "MEMORY_SIZE_BYTES")
    val MemoryDescription = Value(12, "MEMORY_DESCRIPTION")
    val MemorySizeTotal = Value(13, "MEMORY_SIZE_TOTAL")
    val MemoryBanksTotal = Value(14, "MEMORY_BANKS_TOTAL")

    val NicSpeed = Value(15, "NIC_SPEED") // in bits
    val MacAddress = Value(16, "MAC_ADDRESS")
    val NicDescription = Value(17, "NIC_DESCRIPTION")

    val DiskSizeBytes = Value(18, "DISK_SIZE_BYTES")
    val DiskType = Value(19, "DISK_TYPE")
    val DiskDescription = Value(20, "DISK_DESCRIPTION")
    val DiskStorageTotal = Value(21, "DISK_STORAGE_TOTAL")

    val LldpInterfaceName = Value(22, "LLDP_INTERFACE_NAME")
    val LldpChassisName = Value(23, "LLDP_CHASSIS_NAME")
    val LldpChassisIdType = Value(24, "LLDP_CHASSIS_ID_TYPE")
    val LldpChassisIdValue = Value(25, "LLDP_CHASSIS_ID_VALUE")
    val LldpChassisDescription = Value(26, "LLDP_CHASSIS_DESCRIPTION")
    val LldpPortIdType = Value(27, "LLDP_PORT_ID_TYPE")
    val LldpPortIdValue = Value(28, "LLDP_PORT_ID_VALUE")
    val LldpPortDescription = Value(29, "LLDP_PORT_DESCRIPTION")
    val LldpVlanId = Value(30, "LLDP_VLAN_ID")
    val LldpVlanName = Value(31, "LLDP_VLAN_NAME")

    // DO NOT USE - Deprecated
    val NicName = Value(32, "INTERFACE_NAME")
    // DO NOT USE - Deprecated
    val NicAddress = Value(33, "INTERFACE_ADDRESS")
  }

  // Post enum fields, enum is not safe to extend with new values
  type DynamicEnum = AssetMeta
  object DynamicEnum {
    val BaseDescription = findOrCreateFromName("BASE_DESCRIPTION")
    val BaseProduct = findOrCreateFromName("BASE_PRODUCT")
    val BaseVendor = findOrCreateFromName("BASE_VENDOR")
    val BaseSerial = findOrCreateFromName("BASE_SERIAL")

    def getValues(): Seq[AssetMeta] = {
      Seq(BaseDescription, BaseProduct, BaseVendor, BaseSerial)
    }

    def getLshwValues(): Set[AssetMeta] = {
      Set(BaseDescription, BaseProduct, BaseVendor, BaseSerial)
    }
  }
}
