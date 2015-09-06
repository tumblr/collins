package collins.provisioning

/**
 * Describes a profile for use with provisioning
 *
 * This will map to a provisioning profile which is described in the collins docs
 */
case class ProvisionerProfile(
    identifier: String,
    label: String,
    prefix: String,
    allow_suffix: Boolean,
    role: ProvisionerRoleData) extends Ordered[ProvisionerProfile] {
  override def compare(that: ProvisionerProfile): Int = {
    this.label.compare(that.label)
  }
}

