package collins.solr

/**
 * scala has no multi-set, so let's make one!
 *
 * A MultiSet (MSet) is basically a Set that allows duplicate items.  The
 * duplicates are handled by keeping a count with each distinct item.
 *
 * We use MSets in Solr multi-values, since we don't care about the order, but
 * we need to retain duplicates (mainly for NUM_DISKS), and this was easier
 * that writing orderings for all the value types
 *
 * This is a very basic and unoptimized MSet that only supports exactly what we
 * need from it, this should be expanded to fit in more with regular scala
 * collections
 */
case class MultiSet[T] private(val items: Map[T, Int]) {
  def +(v: T) = new MultiSet[T](items.get(v) match {
    case Some(c) => items + (v -> (c + 1))
    case None => items + (v -> 1)
  })

  def toSeq: Seq[T] = items.map{case (item, count) => (1 to count).map{_ => item}}.toSeq.flatten

  def size: Long = items.foldLeft(0){_ + _._2}

  def headOption: Option[T] = items.headOption.map{_._1}
}

object MultiSet {
  def apply[T](items: T*): MultiSet[T] = {
    val m = new MultiSet[T](Map[T, Int]())
    items.foldLeft(m){(build, item) => build + item}
  }

  def fromSeq[T](s: Seq[T]): MultiSet[T] = MultiSet(s:_*)
}
