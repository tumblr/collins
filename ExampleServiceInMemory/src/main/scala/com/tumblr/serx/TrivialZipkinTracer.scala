package com.tumblr.serx
import com.twitter.finagle.tracing.Tracer
import com.twitter.finagle.tracing.TraceId
import com.twitter.finagle.tracing.Record
import scala.collection.mutable.LinkedList

class TrivialZipkinTracer extends Tracer {
  
  val storage = LinkedList[Record]()
  
  override def record(record: Record) {
    storage.append(LinkedList(record))
  }
  
  def sampleTrace(traceId: TraceId): Option[Boolean] = Some(true)
}
