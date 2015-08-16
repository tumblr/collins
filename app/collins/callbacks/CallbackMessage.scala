package collins.callbacks

case class CallbackMessage(name: String, oldValue: CallbackDatumHolder, newValue: CallbackDatumHolder)
