package models.logs

object LogMessageType extends Enumeration {
  type LogMessageType = Value
  val Emergency = Value(0, "EMERGENCY")
  val Alert = Value(1, "ALERT")
  val Critical = Value(2, "CRITICAL")
  val Error = Value(3, "ERROR")
  val Warning = Value(4, "WARNING")
  val Notice = Value(5, "NOTICE")
  val Informational = Value(6, "INFORMATIONAL")
  val Debug = Value(7, "DEBUG")
  val Note = Value(8, "NOTE")
}
