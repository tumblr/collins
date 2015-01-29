package util
package concurrent

import scala.concurrent.duration.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

trait RateLimit {
  def rate: Int
  def perTimeUnit: Long // milliseconds
  def now: Long = System.currentTimeMillis()
}
trait RateLimiter {
  def limit: RateLimit
  def isLimited(subject: String): Boolean
  def tick(subject: String)
  def untick(subject: String)
}

case class BasicRateLimit(rate: Int, perTimeUnit: Long) extends RateLimit {
  override def toString(): String =
    "%d/%dms".format(rate, perTimeUnit)
}

object RateLimit {

  def fromString(input: String): RateLimit = {
    val array = input.split("/", 2)
    if (array.length != 2)
      throw new IllegalArgumentException(
        "Input string '%s' must be of form int/duration".format(input)
      )
    val actionsAllowed = array(0)
    val timeUnit = array(1)
    fromStringPair(actionsAllowed, timeUnit)
  }

  def fromStringPair(actionsAllowed: String, timeUnit: String): RateLimit = {
    val allowedInt = try actionsAllowed.toInt catch {
      case e =>
        throw new IllegalArgumentException("Expected '%s' to be an integer".format(actionsAllowed))
    }
    val duration: Long = try Duration(timeUnit).toMillis catch {
      case e =>
        throw new IllegalArgumentException(
          "Expected '%s' to be a duration such as '10 seconds'".format(timeUnit)
        )
    }
    if (allowedInt < 0)
      throw new IllegalArgumentException("'%s' must not be negative".format(actionsAllowed))
    else if (duration < 0)
      throw new IllegalArgumentException("'%s' must not be negative".format(timeUnit))
    else
      BasicRateLimit(allowedInt, duration)
  }
}

private[concurrent] case class RateBucket(var started: Long, var rateUsage: Int) {
  private val lock = new ReentrantLock()
  def withLock[A](f: RateBucket => A): A = {
    lock.lock()
    try {
      f(this)
    } finally {
      lock.unlock()
    }
  }
}

private[concurrent] case class MemoryRateLimiter(
  limit: RateLimit, buckets: ConcurrentHashMap[String, RateBucket]
) extends RateLimiter
  with RateLimit
{
  override def rate: Int = limit.rate
  override def perTimeUnit: Long = limit.perTimeUnit

  override def isLimited(subject: String): Boolean = {
    buckets.putIfAbsent(subject, RateBucket(now-1, 0))
    if (rate == 0) {
      true
    } else if (rate == 1 && perTimeUnit == 0L) {
      false
    } else {
      buckets.get(subject).withLock { bucket =>
        if (expired(now, bucket.started))
          false
        else
          bucket.rateUsage >= rate
      }
    }
  }

  protected[concurrent] def bucketRateUsage(subject: String): Int = {
    if (!buckets.containsKey(subject))
      0
    else
      buckets.get(subject).rateUsage
  }

  // If a user sets the perTimeUnit to 0, it will cause the rate bucket to be reset on every tick.
  override def tick(subject: String) {
    buckets.putIfAbsent(subject, RateBucket(now-1, 0))
    buckets.get(subject).withLock { bucket =>
      val current = now
      if (expired(current, bucket.started)) {
        bucket.started = current
        bucket.rateUsage = 0
      }
      bucket.rateUsage += 1
    }
  }
  override def untick(subject: String) {
    buckets.putIfAbsent(subject, RateBucket(now-1, 0))
    buckets.get(subject).withLock { bucket =>
      if (bucket.rateUsage > 0)
        bucket.rateUsage -= 1
    }
  }
  private def expired(current: Long, started: Long): Boolean =
    (current - started) >= perTimeUnit
}

object RateLimiter {
  lazy private val buckets = new ConcurrentHashMap[String,RateBucket]()
  def apply(rateLimit: String): RateLimiter =
    MemoryRateLimiter(RateLimit.fromString(rateLimit), buckets)
}
