package util

import com.twitter.util.StorageUnit

class BitStorageUnit(val bits: Long) extends StorageUnit(bits/8) {
  require(bits > 0, "bits must be > 0")

  def inBits = bits
  def inKilobits = bits / (1000L)
  def inMegabits = bits / (1000L * 1000)
  def inGigabits = bits / (1000L * 1000 * 1000)
  def inTerabits = bits / (1000L * 1000 * 1000 * 1000)

  override def toString() = toHuman()
  override def toHuman(): String = {
    val sizes = Array("Bits", "Kb", "Mb", "Gb", "Tb")
    if ( bits == 0 ) { return "0 bits" }
    val i = math.floor(math.log(bits) / math.log(1000)).toInt
    val small = bits / math.pow(1000, i).toDouble
    (i == 0) match {
      case true => "%d %s".format(bits, sizes(i))
      case false => "%.2f %s".format(small, sizes(i))
    }
  }
}
object BitStorageUnit {
  def apply(bits: Long) = new BitStorageUnit(bits)
}
