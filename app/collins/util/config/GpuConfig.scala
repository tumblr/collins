package collins.util.config

object GpuConfig extends Configurable {
  override val namespace = "gpu"
  override val referenceConfigFilename = "gpu_reference.conf"

  def gpuVendors= getStringSet("gpuVendors")

  override protected def validateConfig() {
    gpuVendors 
  }
}
