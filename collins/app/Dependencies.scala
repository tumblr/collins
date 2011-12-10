package object App {
  import play.api.Play._

  val Resources: controllers.Resources = if (isDev) {
    new controllers.Resources with controllers.SecureWebController
  } else {
    new controllers.Resources with controllers.SecureApiController
  }

}
