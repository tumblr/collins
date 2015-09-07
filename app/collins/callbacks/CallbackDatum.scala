package collins.callbacks

trait CallbackDatum {
  def compare(z: Any): Boolean
}

case class StringDatum(val e: String) extends CallbackDatum {
  override def compare(z: Any): Boolean = {
    if (z == null)
      return false
    val ar = z.asInstanceOf[AnyRef]
    if (ar.getClass != classOf[StringDatum])
      false
    else {
      val other = ar.asInstanceOf[StringDatum]
      this.e.equals(other.e)
    }
  }
}

case class CallbackDatumHolder(val datum: Option[CallbackDatum]) {
  override def equals(z: Any): Boolean = {
    if (z == null)
      return false
    val ar = z.asInstanceOf[AnyRef]
    if (ar.getClass != classOf[CallbackDatumHolder])
      false
    else {
      val other = ar.asInstanceOf[CallbackDatumHolder]
      (this.datum, other.datum) match {
        case (Some(s), Some(t)) => s.compare(t)
        case _                  => false
      }
    }
  }
}