package models

import play.api.json._

case class PageParams(page: Int, size: Int, sort: String, offset: Int = 0)
case class Page[A](items: Seq[A], page: Int, offset: Long, total: Long) {
  lazy val prev: Option[Int] = Option(page - 1).filter(_ >= 0)
  lazy val next: Option[Int] = Option(page + 1).filter(_ => (offset + items.size) < total)

  lazy val prevPage = prev.getOrElse(0)
  lazy val nextPage = next.getOrElse(page)

  def getPaginationHeaders(): Seq[(String,String)] = {
    Seq(
      ("X-Pagination-PreviousPage" -> prevPage.toString),
      ("X-Pagination-CurrentPage" -> page.toString),
      ("X-Pagination-NextPage" -> nextPage.toString),
      ("X-Pagination-TotalResults" -> total.toString)
    )
  }
  def getPaginationJsMap(): Map[String, JsValue] = {
    Map(
      "Pagination" -> JsObject(Map(
        "PreviousPage" -> JsNumber(prevPage),
        "CurrentPage" -> JsNumber(page),
        "NextPage" -> JsNumber(nextPage),
        "TotalResults" -> JsNumber(total)
      ))
    )
  }
}
