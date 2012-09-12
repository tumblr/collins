package util.security

import models.UserImpl
import scala.collection.immutable.MapProxy

// This exists because a Guava cache loader is invariant in type K, this is an easy work around
case class FileUserMap(override val self: Map[String,UserImpl]) extends MapProxy[String,UserImpl]
