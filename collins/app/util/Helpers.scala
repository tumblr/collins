package util

object Helpers {
  def formatPowerPort(label: String) = {
    import models.AssetMeta.Enum.PowerPort
    PowerPort.toString + "_" + label
  }
}
