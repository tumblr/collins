package collins.firehose

sealed abstract class Category

case object Category {
  case object Asset extends Category
  case object Meta extends Category
  case object IpAddress extends Category
}