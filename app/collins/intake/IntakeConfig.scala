package collins.intake

import util.config.Configurable

object IntakeConfig extends Configurable {

  override val namespace = "intake"
  override val referenceConfigFilename = "intake_variables_reference.conf"

  def intake_field_names = getStringSet("params")

  override protected def validateConfig() {

      intake_field_names
  }

}
