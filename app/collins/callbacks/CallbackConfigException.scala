package collins.callbacks

case class CallbackConfigException(source: String, key: String)
  extends Exception("Didn't find key %s in callback configuration for %s".format(key, source))
