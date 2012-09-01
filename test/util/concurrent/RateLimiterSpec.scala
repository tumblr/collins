package util
package concurrent

import org.specs2.specification.Scope
import org.specs2.mutable._
import org.specs2.mock._

import scala.collection.parallel.immutable.ParRange
import scala.collection.JavaConverters._
import java.util.concurrent.{ArrayBlockingQueue, CountDownLatch, ConcurrentHashMap}

class RateLimiterSpec extends Specification {

  class RateLimitScope(cfg: String) extends Scope {
    def rateLimit = RateLimit.fromString(cfg)
    def rate = rateLimit.rate
    def perTimeUnit = rateLimit.perTimeUnit
  }
  "Rate Limit" should {
    "Parse Limit Specifications" in {
      "1/30 seconds" in new RateLimitScope("1/30 seconds") {
        rate === 1
        perTimeUnit === 30000L
      }
      "5/1 minute" in new RateLimitScope("5/1 minute") {
        rate === 5
        perTimeUnit === 60000L
      }
      "1/20 seconds" in new RateLimitScope("1/20 seconds") {
        rate === 1
        perTimeUnit === 20000L
      }
      "1/0 seconds" in new RateLimitScope("1/0 seconds") {
        rate === 1
        perTimeUnit === 0L
      }
      "0/0 seconds" in new RateLimitScope("0/0 seconds") {
        rate === 0
        perTimeUnit === 0L
      }
    }
    "Throw exceptions for invalid specifications" in {
      "1 minute" in new RateLimitScope("1 minute") {
        rateLimit must throwA[IllegalArgumentException]
      }
      "noInt/5 seconds" in new RateLimitScope("noInt/5 seconds") {
        rateLimit must throwA[IllegalArgumentException]
      }
      "1/noDuration" in new RateLimitScope("1/noDuration") {
        rateLimit must throwA[IllegalArgumentException]
      }
      "-1/15 seconds" in new RateLimitScope("-1/15 seconds") {
        rateLimit must throwA[IllegalArgumentException]
      }
      "1/-15 seconds" in new RateLimitScope("1/-15 seconds") {
        rateLimit must throwA[IllegalArgumentException]
      }
    }
  }

  class RateLimiterScope(cfg: String, val key: String) extends Scope {
    lazy val rateLimit = RateLimit.fromString(cfg)
    lazy val rate = rateLimit.rate
    lazy val perTimeUnit = rateLimit.perTimeUnit
    lazy val buckets = new ConcurrentHashMap[String,RateBucket]()
    lazy val limiter = new MemoryRateLimiter(rateLimit, buckets)
    def isLimited() = limiter.isLimited(key)
    def tick() = limiter.tick(key)
    def run(count: Int)(f: Int => Unit): List[Tuple2[Int,Boolean]] = {
      val range = ParRange(0, count, 1, false)
      val limits = new ArrayBlockingQueue[Tuple2[Int,Boolean]](count*2)
      val latch = new CountDownLatch(count)
      range.foreach { i =>
        val gotLimited = isLimited
        tick
        f(i)
        limits.offer((i, gotLimited))
        latch.countDown()
      }
      latch.await
      limits.asScala.toList
    }
  }

  "Rate Limiter" should {

    "Limit you when appropriate" in {
      "config=1/1 seconds, do 2 in 1 second" in new RateLimiterScope("1/1 seconds", "bob") {
        isLimited must beFalse
        tick
        isLimited must beTrue
      }
      "config=1/30 ms, get limited then sleep" in new RateLimiterScope("1/30 ms", "bob") {
        isLimited must beFalse
        tick
        isLimited must beTrue
        Thread.sleep(50)
        isLimited must beFalse
      }
      "config=50/1 second, 100 concurrent tries" in new RateLimiterScope("50/1 second", "uncle") {
        val results = run(100) {i => }
        val gotLimited = results.filter(res => res._2).size
        gotLimited must beCloseTo(50, 20)
      }
    }

    "Not limit you when appropriate" in new RateLimiterScope("500/1 second", "uncle") {
      val results = run(500) { i => }
      val gotLimited = results.filter(res => res._2).size
      gotLimited === 0
    }

    "Handle disabled rate limiting" in new RateLimiterScope("1/0 seconds", "jada") {
      val results = run(500) { i => }
      val gotLimited = results.filter(res => res._2).size
      gotLimited === 0
    }

    "Handle always rate limiting (0/0 seconds)" in new RateLimiterScope("0/0 seconds", "jada") {
      val results = run(500) { i => }
      val gotLimited = results.filter(res => res._2).size
      gotLimited === 500
    }

  }

}
