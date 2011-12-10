package object App {
  import play.api.Play._

  val Resources: controllers.Resources = new controllers.Resources with controllers.SecureWebController
  val HelpPage: controllers.HelpPage = new controllers.HelpPage with controllers.SecureWebController
  val Api: controllers.Api = new controllers.Api with controllers.SecureApiController

}
