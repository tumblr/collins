package util

import test.{MutedLogger, FileHelper}
import org.specs2.specification.Scope
import org.specs2.mutable._
import org.specs2.time.Duration

import java.util.concurrent.atomic.AtomicInteger
import java.io.File

class FileWatcherSpec extends Specification with MutedLogger {
  "File Watcher" should {
    "if an error occurs" in {
      val badFilename = "I do not exist"
      "trigger onError" in {
        watcher(badFilename, 1.second).tick(5).errorCount mustEqual 5
      }
      "do not trigger onChange" in {
        watcher(badFilename, 1.second).tick(5).changeCount mustEqual 0
      }
    }
    "if an error eventually occurs" in {
      "trigger onError" in {
        val w = FileHelper.createRandomFile("Hello World") { f =>
          val i = watcher(f.getAbsolutePath, 100.millis).tick(5)
          checkCount(i, 0, 1)
          i
        }
        w.waitFor(150.millis).tick(1).errorCount mustEqual 1
      }
      "recover" in {
        val f = FileHelper.createThenUnlink
        val w = watcher(f.getAbsolutePath, 100.millis).tick(5)
        w.errorCount mustEqual 5
        FileHelper.append(f, "I now exist")
        w.tick(1).changeCount mustEqual 1
      }
      "recover after initial success" in {
        val f = FileHelper.createRandomFile(Some("Hello World"))
        val w = watcher(f.getAbsolutePath, 100.millis).tick(5)
        checkCount(w, 0, 1)
        FileHelper.unlinkFile(f)
        checkCount(w.waitFor(150.millis).tick(4), 4, 1)
        FileHelper.append(f, "I exist again!")
        checkCount(w.tick(1), 4, 2)
      }
    }
    "Not trigger onChange if the timeout is reached but the file isn't changed" in {
      FileHelper.createRandomFile("Hello World") { f =>
        val w = watcher(f.getAbsolutePath, 100.millis).tick(5)
        checkCount(w, 0, 1)
        checkCount(w.waitFor(300.millis).tick(1), 0, 1)
      }
    }
    // This test is pretty brittle. on OS X it takes ~ 1000ms for the OS to update the file
    "Only trigger onChange when the specified file is changed" in {
      val testTime = 2.seconds
      FileHelper.createRandomFile("Hello World") { f =>
        val otime = f.lastModified
        val w = watcher(f.getAbsolutePath, testTime).tick(1)
        checkCount(w, 0, 1)
        writeFor(f, testTime)
        checkCount(w.waitFor(150.millis).tick(1), 0, 2)
        val mtime = f.lastModified
        mtime must be_>(otime)
      }
    }
  }

  def writeFor(f: File, d: Duration) {
    val otime = f.lastModified
    var mtime = 0L
    val timeToExpire = System.currentTimeMillis + d.inMillis
    while (System.currentTimeMillis < timeToExpire) {
      if ( FileHelper.lastModified(f.getAbsolutePath) <= otime ) {
        FileHelper.append(f, FileHelper.randomContent(1024))
        Thread.sleep(100L)
      } else if (mtime == 0L) {
        mtime = System.currentTimeMillis
        println("Sleeping waiting for timeout to expire")
        Thread.sleep(math.abs(timeToExpire - mtime) + 1)
      }
    }
  }
  def watcher(file: String, waits: Duration): TestFileWatcher = {
    TestFileWatcher(file, waits, 0.seconds)
  }
  def checkCount(i: TestFileWatcher, e: Int, c: Int) = {
    i.errorCount mustEqual e
    i.changeCount mustEqual c
  }
}

case class TestFileWatcher(
  file: String,
  wTime: Duration,
  private val initialSleepTime: Duration
) extends FileWatcher {
  @volatile private var _sleepTime: Duration = initialSleepTime
  val onChangeCount = new AtomicInteger(0)
  val onErrorCount = new AtomicInteger(0)

  override protected val filename = file
  override protected val millisBetweenFileChecks = wTime.inMillis
  def changeCount = onChangeCount.get
  def errorCount = onErrorCount.get
  private def sleepTime = _sleepTime.inMillis
  def tick(t: Int): TestFileWatcher = {
    for (i <- 0 until t) {
      logger.debug("tick %d".format(i))
      tick()
      Thread.sleep(sleepTime)
    }
    this
  }
  def waitFor(d: Duration) = {
    logger.debug("waitFor %d millis".format(d.inMillis))
    Thread.sleep(d.inMillis)
    this
  }
  def sleepBetweenTicks(d: Duration) = {
    _sleepTime = d
    this
  }
  override protected def onChange(file: File) {
    onChangeCount.incrementAndGet
  }
  override protected def onError(file: File) {
    onErrorCount.incrementAndGet
  }
}


