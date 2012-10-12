package collins.solr


/**
 * needs some work, currently only a few tests use this.
 */
object CollinsQueryDSL {
  class CollinsQueryString(val s: String) {
    lazy val query: SolrExpression = CollinsQueryParser().parseQuery(s).fold(
      err => throw new Exception("CQL error: " + err),
      expr => expr
    )
  }
  implicit def str2SolrStringValue(s: String) = SolrStringValue(s)
  implicit def strsolr_tuple2keyval(t: Tuple2[String, SolrSingleValue]): SolrKeyVal = SolrKeyVal(t._1, t._2)
  implicit def str2collins(s: String): CollinsQueryString = new CollinsQueryString(s)
  implicit def collins2str(c: CollinsQueryString): String = c.s
  implicit def int_tuple2keyval(t: Tuple2[String, Int]):SolrKeyVal = SolrKeyVal(t._1, SolrIntValue(t._2))
  implicit def string_tuple2keyval(t: Tuple2[String, String]):SolrKeyVal = SolrKeyVal(t._1, SolrStringValue(t._2))
  implicit def double_tuple2keyval(t: Tuple2[String, Double]):SolrKeyVal = SolrKeyVal(t._1, SolrDoubleValue(t._2))
  implicit def boolean_tuple2keyval(t: Tuple2[String, Boolean]):SolrKeyVal = SolrKeyVal(t._1, SolrBooleanValue(t._2))

  def not(exp: SolrExpression) = SolrNotOp(exp)



}

