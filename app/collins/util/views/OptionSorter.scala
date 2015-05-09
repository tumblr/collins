package collins.util.views

import collins.util.views.Formatter.camelCase

object OptionSorter {

  def sortStrings(seq: Seq[AnyRef], doCamelCase: Option[String] = None): Seq[(String,String)] = {
    val newSeq = seq.map(_.toString).sortWith(_.compareTo(_) < 0)
    doCamelCase match {
      case Some(sep) => newSeq.map(s => (s -> camelCase(s, sep)))
      case None => newSeq.map(s => (s -> s))
    }
  }

}
