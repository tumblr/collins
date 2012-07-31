package views.html

package object twitterBootstrap {

  import helper.{FieldConstructor, FieldElements}

  implicit val twitterBootstrapField = new FieldConstructor {
    def apply(elts: FieldElements) = twitterBootstrapFieldConstructor(elts)
  }
}
