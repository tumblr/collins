package models

import java.util.Date
import java.sql.Timestamp

sealed private[models] class DateToTimestamp(date: Date) {
  def asTimestamp(): Timestamp = new Timestamp(date.getTime())
}

object conversions {
  implicit def dateToTimestamp(date: Date) = new DateToTimestamp(date)
}
