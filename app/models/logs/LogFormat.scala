package models.logs

object LogFormat extends Enumeration {
  type LogFormat = LogFormat.Value
  val PlainText = Value(0, "text/plain")
  val Json = Value(1, "application/json")
}
