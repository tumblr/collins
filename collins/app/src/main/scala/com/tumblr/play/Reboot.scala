package com.tumblr.play

sealed trait RebootType
object RebootType {
  def soft() = RebootSoft
  def hard() = RebootHard
  def unapply(t: String) = t.toLowerCase match {
    case "soft" => Some(RebootType.soft())
    case "hard" => Some(RebootType.hard())
    case _ => None
  }
}
case object RebootSoft extends RebootType {
  override def toString: String = "soft"
}
case object RebootHard extends RebootType {
  override def toString: String = "hard"
}

