package collins.util.config

object GpuConfig extends Configurable {
  override val namespace = "gpu"
  override val referenceConfigFilename = "gpu_reference.conf"

  def supportedVendorStrings= getStringSet("supportedVendorStrings", Set("NVIDIA Corporation"))

  override protected def validateConfig() {
    supportedVendorStrings
  }
}
