package collins.cache

trait CacheStats {
  def evictionCount(): Long
  def hitCount(): Long
  def hitRate(): Double
  def missCount(): Long
  def missRate(): Double
  def requestCount(): Long
}
