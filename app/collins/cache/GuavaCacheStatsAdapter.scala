package collins.cache

import com.google.common.cache.{CacheStats => GuavaCacheStats}

class GuavaCacheStatsAdapter(stats: GuavaCacheStats) extends CacheStats {
  def evictionCount(): Long = stats.evictionCount
  def hitCount(): Long = stats.hitCount
  def hitRate(): Double = stats.hitRate
  def missCount(): Long = stats.missCount
  def missRate(): Double = stats.missRate
  def requestCount(): Long = stats.requestCount
}
