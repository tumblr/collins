package models

import play.api.libs.json._

case class PageParams(page: Int, size: Int, sort: String) {
  def offset: Int = page * size
  def validate() {
    require(page >= 0, "page must be >= 0")
    require(size >= 0, "size must be >= 0")
  }

  def toSeq = List("page" -> page.toString, "size" -> size.toString, "sort" -> sort)
}

sealed trait SortDirection {
  val strVal: String
}
case object SortAsc extends SortDirection {
  val strVal = "ASC"
}
case object SortDesc extends SortDirection {
  val strVal = "DESC"
}



object SortDirection {

  val values = SortAsc :: SortDesc :: Nil

  def withName(str: String):Option[SortDirection] = values.find{_.strVal == str}

  def op(dir: SortDirection): (Int, Int) => Boolean = if (dir == SortAsc) _ < _ else _ > _ 
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
