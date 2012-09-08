package models.logs

object LogSource extends Enumeration {
  type LogSource = Value
  val Internal = Value(0, "INTERNAL")
  val Api = Value(1, "API")
  val User = Value(2, "USER")
  val System = Value(3, "SYSTEM")
}
