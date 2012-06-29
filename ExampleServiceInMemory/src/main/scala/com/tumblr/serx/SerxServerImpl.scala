package com.tumblr.serx

import com.twitter.finagle.builder.{Server => BuiltServer, ServerBuilder}
import com.tumblr.finagle.http._
import com.tumblr.logging.Logging
import org.jboss.netty.buffer.ChannelBuffers.copiedBuffer
import org.jboss.netty.util.CharsetUtil.UTF_8
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import com.twitter.conversions.time._
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Http, Request, Response, RichHttp, Status}
import com.twitter.finagle.stats.OstrichStatsReceiver
import com.twitter.finagle.tracing.Trace
import com.twitter.util.{Duration, Future}
import com.twitter.ostrich.admin.{Service => OstrichService}
import java.net.InetSocketAddress
import config._
import com.twitter.finagle.builder.{Server, ServerBuilder}
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finagle.thrift.ThriftServerFramedCodec
import com.twitter.finagle.tracing.{ConsoleTracer, NullTracer, Tracer}
import org.apache.thrift.protocol._
import com.twitter.finagle.tracing.TraceId
import com.twitter.finagle.tracing.Record
import com.twitter.finagle.zipkin.thrift.ZipkinTracer

trait SerxServiceServer extends OstrichService with Logging {
  val serverName: String
  val service: SerxService.FutureIface
  protected def defaultShutdownTimeout: Duration = 5.seconds
}

class SerxServerImpl(config: SerxServiceConfig, val service: SerxService.FutureIface) extends SerxServiceServer {
  val serverName = "SerxServer"
  var thriftServer: Option[SerxServiceServer] = None
  var httpServer: Option[SerxServiceServer] = None

  def start = {
    log.info("Starting SerxServer")
    thriftServer.isDefined match {
      case true => throw new IllegalStateException("Server start already called")
      case false =>
        val _thriftServer = new SerxThriftServerImpl(config, service)
        _thriftServer.start()
        thriftServer = Some(_thriftServer)
    }
    httpServer.isDefined match {
      case true => throw new IllegalStateException("Server start already called")
      case false =>
        val _httpServer = new SerxHttpServerImpl(config, service)
        _httpServer.start()
        httpServer = Some(_httpServer)
    }
  }
  def shutdown = {
    log.info("Shutting down SerxServer")
    List(thriftServer, httpServer) foreach { server =>
      server.foreach { _.shutdown() }
    }
  }
}


class SerxThriftServerImpl(config: SerxServiceConfig, val service: SerxService.FutureIface) extends SerxServiceServer {
  val thriftPort: Int = config.thriftPort
  val serverName: String = "SerxThriftServer"

  def thriftCodec = ThriftServerFramedCodec()
  def statsReceiver: StatsReceiver = new OstrichStatsReceiver
  def tracerFactory: Tracer.Factory = NullTracer.factory
  val thriftProtocolFactory: TProtocolFactory = new TBinaryProtocol.Factory()
  
  var server: Option[Server] = None
  
  def serverBuilder =
    ServerBuilder()
      .codec(thriftCodec)
      .name(serverName)
      .reportTo(statsReceiver)
      .tracerFactory(tracerFactory)
      .bindTo(new InetSocketAddress(thriftPort))
  
  override def start() {
    if (!server.isDefined) {
      val thriftImpl = new SerxService.FinagledService(service, thriftProtocolFactory)
      server = Some(serverBuilder.build(thriftImpl))
    }
  }

  override def shutdown() {
    log.info("Shutting down %s", serverName)
    server.foreach { srv =>
      srv.close(defaultShutdownTimeout)
    }
  }
}

class SerxHttpServerImpl(config: SerxServiceConfig, val service: SerxService.FutureIface) extends SerxServiceServer {
  val serverName = "SerxHttpServer"
  val httpPort: Int = config.httpPort

  protected val serverSpec = ServerBuilder()
        .codec(RichHttp[Request](Http()))
        .bindTo(new InetSocketAddress(httpPort))
        .name(serverName)
//        .tracerFactory(ConsoleTracer.factory)
        .tracerFactory(ZipkinTracer(scribeHost = "0.0.0.0", scribePort = 9900, sampleRate = 1.0.asInstanceOf[Float]))
        .reportTo(new OstrichStatsReceiver)
  protected var server: Option[BuiltServer] = None
  protected val httpService = new HttpService()

  def start() {
    log.info("Starting %s", serverName)
    server.isDefined match {
      case true => throw new IllegalStateException("Start already called on HTTP instance")
      case false =>
        server = Some(serverSpec.build(httpService))
    }
  }

  def shutdown = synchronized {
    log.info("Shutting down %s", serverName)
    server.foreach { _.close(defaultShutdownTimeout) }
  }

  class HttpService extends Service[Request, Response] {
    protected def handleError(error: Throwable) = error match {
      case error: SerxException => RespondError(error, error.description)
      case error => RespondError(error)
    }

    def apply(request: Request) = {
      Get(request, "/.*").map({ req =>
        service.get(req.uri.substring(1)) map { response =>
          Respond(response)
        } handle { case error => handleError(error) }
      }).orElse(Post(request, "^/put$", List("key", "value")).map({ case (req, params) =>
        service.put(params("key"), params("value")) map { response =>
          Respond("ok")
        } handle { case error => handleError(error) }
      })).orElse(Post(request, "^/multiPut$", List("key", "value", "key2", "value2")).map({ case (req, params) =>
        service.multiPut(params("key"), params("value"), params("key2"), params("value2")) map { response =>
          Respond("ok")
        } handle { case error => handleError(error) }
      })).orElse(Post(request, "^/clear$") map { req =>
        service.clear() map { response =>
          Respond("ok")
        } handle { case error => handleError(error) }
      }).getOrElse(Future(Respond("No such endpoint", Status.NotFound)))
    }
  }

}

