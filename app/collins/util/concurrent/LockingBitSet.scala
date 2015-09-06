package collins.util.concurrent

import java.util.BitSet
import java.util.concurrent.locks.ReentrantReadWriteLock

object LockingBitSet {
  def apply(initialSize: Int): LockingBitSet = {
    require(initialSize >= 0, "initialSize must be >= 0")
    new LockingBitSet(new BitSet(initialSize))
  }
}

case class LockingBitSet(private val bs: BitSet) {
  private[this] val lock = new ReentrantReadWriteLock()
  private[this] val readLock = lock.readLock
  private[this] val writeLock = lock.writeLock

  // Help iterate over used bits in a bit set
  case class Biterator(bs: BitSet) extends Iterator[Int] {
    private var pos = bs.nextSetBit(0)
    override def hasNext() = (pos >= 0)
    override def next() = {
      val ret = pos
      pos = bs.nextSetBit(pos + 1)
      ret
    }
  }
  def indexIterator: Iterator[Int] = {
    Biterator(bs.clone.asInstanceOf[BitSet])
  }

  def forRead[T](f: BitSet => T): T = {
    readLock.lock
    try {
      f(bs)
    } finally {
      readLock.unlock
    }
  }
  def forWrite[T](f: BitSet => T): T = {
    writeLock.lock
    try {
      f(bs)
    } finally {
      writeLock.unlock
    }
  }
}
