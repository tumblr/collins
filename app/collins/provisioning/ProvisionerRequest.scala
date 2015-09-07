package collins.provisioning

/**
 * Describes a request to provision an asset.
 *
 * @param token used for identifying an asset, likely the tag
 * @param profile see ProvisionerProfile
 * @param notification an optional address for user notification
 * @param suffix an optional hostname suffix
 */
case class ProvisionerRequest(
  token: String,
  profile: ProvisionerProfile,
  notification: Option[String] = None,
  suffix: Option[String] = None)
