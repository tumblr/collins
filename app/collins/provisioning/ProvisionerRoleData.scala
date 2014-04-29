package collins.provisioning

/**
 * Part of the provisioning profile, describes the role requirements as well as default roles for an
 * asset being provisioned
 */
case class ProvisionerRoleData(
  primary_role: Option[String],
  pool: Option[String],
  secondary_role: Option[String],
  contact: Option[String],
  contact_notes: Option[String],
  attributes: Option[Map[String,String]],
  requires_primary_role: Boolean,
  requires_pool: Boolean,
  requires_secondary_role: Boolean
) {
  def this() = this(None,None,None,None,None,None,false,false,false)
}


