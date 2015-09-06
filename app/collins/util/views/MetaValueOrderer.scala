package collins.util.views

import collins.models.MetaWrapper

// used by show_overview.scala.html
object MetaValueOrderer {
  // returns a tuple where _1 is the number of values with the same name (e.g.
  // number of dimensions), _2 is the wrapper
  def order(mvs: Seq[MetaWrapper]): Seq[Tuple2[Int, MetaWrapper]] = {
    mvs.groupBy(_.getName).values.toSeq.map { values =>
      // convert to _1 is size, _2 is seq[meta wrapper]
      (values.size -> values)
    }.foldLeft(Seq[Tuple2[Int, MetaWrapper]]()) {
      case (total, cur) =>
        // create new sequence in desired format
        total ++ cur._2.map(m => (cur._1 -> m))
    }.sortBy {
      case (size, value) =>
        // sort by name/group id
        "%s-%d".format(value.getLabel, value.getGroupId)
    }
  }
}
