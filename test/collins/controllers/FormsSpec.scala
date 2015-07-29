package collins.controllers

import org.specs2._
import specification._

import collins.controllers.forms._

class FormsSpec extends mutable.Specification {

  "Solr expression format should correctly bind a valid query" in {
    val result = SolrExpressionFormat.bind("query", Map("query" -> "tag=TRQ* AND name=SomeThing"))
    result must beRight
  }

  "Solr expression format should return errors on invalid query" in {
    val result = SolrExpressionFormat.bind("query", Map("query" -> "!A#!!"))
    result must beLeft
  }

  "Solr expression format must return valid query by interpreting as a tag" in {
    val result = SolrExpressionFormat.bind("query", Map("query" -> "TRQ12"))
    result must beRight
  }

  "Solr expression format must return valid query by interpreting as a tag with whitespace" in {
    val result = SolrExpressionFormat.bind("query", Map("query" -> "  TRQ12  "))
    result must beRight
  }
}