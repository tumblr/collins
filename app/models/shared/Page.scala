package models

import play.api.libs.json._

sealed trait SortDirection {
  import SortDirection._
  val strVal: String

  override def toString = strVal

  def unary_! = this match {
    case SortAsc => SortDesc
    case SortDesc => SortAsc
  }
}
object SortDirection {

  case object SortAsc extends SortDirection {
    val strVal = "ASC"
  }
  case object SortDesc extends SortDirection {
    val strVal = "DESC"
  }

  val values = SortAsc :: SortDesc :: Nil

  def withName(str: String):Option[SortDirection] = values.find{_.strVal == str}

  def op(dir: SortDirection): (Int, Int) => Boolean = if (dir == SortAsc) _ < _ else _ > _ 
}

import SortDirection._

case class PageParams(page: Int, size: Int, sort: SortDirection) {
  def offset: Int = page * size
  def validate() {
    require(page >= 0, "page must be >= 0")
    require(size >= 0, "size must be >= 0")
  }

  def toSeq = List("page" -> page.toString, "size" -> size.toString, "sort" -> sort.toString)
}
object PageParams {

  /**
   * Currently if sort is invalid it will just default to Asc
   */
  def apply(page: Int, size: Int, sort: String): PageParams = PageParams(page, size, SortDirection.withName(sort.toUpperCase).getOrElse(SortAsc))
}





case class Page[A](items: Seq[A], page: Int, offset: Long, total: Long) {
  lazy val prev: Option[Int] = Option(page - 1).filter(_ >= 0)
  lazy val next: Option[Int] = Option(page + 1).filter(_ => (offset + items.size) < total)

  lazy val prevPage = prev.getOrElse(0)
  lazy val nextPage = next.getOrElse(page)

  lazy val size = items.size

  def getPaginationHeaders(): Seq[(String,String)] = {
    Seq(
      ("X-Pagination-PreviousPage" -> prevPage.toString),
      ("X-Pagination-CurrentPage" -> page.toString),
      ("X-Pagination-NextPage" -> nextPage.toString),
      ("X-Pagination-TotalResults" -> total.toString)
    )
  }
  def getPaginationJsObject(): Seq[(String, JsValue)] = {
    Seq(
      "Pagination" -> JsObject(Seq(
        "PreviousPage" -> JsNumber(prevPage),
        "CurrentPage" -> JsNumber(page),
        "NextPage" -> JsNumber(nextPage),
        "TotalResults" -> JsNumber(total)
      ))
    )
  }
}

object Page {
  def emptyPage[A] = Page[A](Nil, 0, 0, 0)
}
