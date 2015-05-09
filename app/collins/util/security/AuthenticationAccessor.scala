package collins.util.security

trait AuthenticationAccessor {
  def getAuthentication(): AuthenticationProvider
}
