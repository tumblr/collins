package util

import com.twitter.util.StorageUnit

object ByteStorageUnit {
  def apply(bytes: Long) = new ByteStorageUnit(bytes)
}
class ByteStorageUnit(_bytes: Long) extends StorageUnit(_bytes) {

  override def toString() = toHuman()

  override def toHuman(): String = {
    val sizes = Array("Bytes", "KB", "MB", "GB", "TB", "PB")
    if ( bytes == 0 ) { return "0 Bytes" }
    val i = math.floor(math.log(bytes) / math.log(1024)).toInt
    val small = bytes / math.pow(1024, i).toDouble
    (i == 0) match {
      case true => "%d %s".format(bytes, sizes(i))
      case false => "%.2f %s".format(small, sizes(i))
    }
  }

}
