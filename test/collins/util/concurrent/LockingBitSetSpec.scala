package collins.util.concurrent

import org.specs2.specification.Scope
import org.specs2.mutable._
import org.specs2.mock._

import scala.collection.parallel.immutable.ParRange
import scala.util.Random.nextBoolean
import scala.collection.JavaConverters._
import java.util.concurrent.{ArrayBlockingQueue, CountDownLatch, ConcurrentHashMap}

class LockingBitSetSpec extends Specification {
  class BitSetScope(val initSize: Int) extends Scope {
    val bitSet = LockingBitSet(initSize)
    def run(count: Int): List[Tuple2[Int,Boolean]] = {
      val range = ParRange(0, count, 1, false)
      val results = new ArrayBlockingQueue[Tuple2[Int,Boolean]](count*2)
      val latch = new CountDownLatch(count)
      range.foreach { i =>
        val bool = nextBoolean
        bitSet.forWrite(_.set(i, bool))
        results.offer((i, bool))
        latch.countDown()
      }
      latch.await
      results.asScala.toList
    }
  }

  "A Locking Bit Set" should {
    "Ensure write safety" in new BitSetScope(4096) {
      val results = run(initSize)
      results.foreach { case(idx,res) =>
        bitSet.forRead(_.get(idx)) === res
      }
    }

    "Provide an iterator over set values" in new BitSetScope(128) {
      val results = run(initSize)
      val set = results.filter(_._2).map(_._1)
      bitSet.indexIterator.toList must haveTheSameElementsAs(set)
    }
  }

}
