package util

import play.api.Logger

import java.io.File
import java.util.Date
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

/**
 * Watch a file for changes and get notified when it has.
 * Uou must call tick on every data access, e.g.
 * This will always notify the first time that tick is called.
 */
object FileWatcher {
  def watch(file: String, secondsBetweenChecks: Int = 30)(changeFn: File => Unit): FileWatcher = {
    fileGuard(file) // ensure file exists
    new FileWatcher {
      override protected val filename = file
      override protected val millisBetweenFileChecks = secondsBetweenChecks*1000L
      override protected def onChange(file: File) = changeFn(file)
      override protected def onError(file: File) = {}
    }
  }
  def watchWithResults[T]
    (file: String, default: T, secondsBetweenChecks: Int = 30)
    (cf: File => T): FileWatcherResults[T] =
  {
    fileGuard(file)
    new FileWatcherResults[T] {
      override protected val filename = file
      override protected val millisBetweenFileChecks = secondsBetweenChecks*1000L
      override protected val init = default
      override protected def fromFile(file: File) = cf(file)
      override protected def onError(file: File) = {}
    }
  }

  def fileGuard(filename: String): File = {
    val file = new File(filename)
    require(isFileReadable(file), "File %s does not exist".format(filename))
    file
  }
  def isFileReadable(file: File): Boolean = file.exists() && file.isFile() && file.canRead()
}

trait FileWatcher {
  // Last modification time of the file
  @volatile private var lastModificationTime = 0L
  // Last time we actually read the mtime from the file
  @volatile private var lastTimeFileChecked = 0L
  protected val logger = Logger(getClass)

  // How many millis to wait between file checks
  protected def millisBetweenFileChecks: Long
  // Name of file to check
  protected def filename: String
  // Method to call when a change is seen (including the first time the file is checked)
  protected def onChange(file: File)
  // Method to call if file can't be read
  protected def onError(file: File)

  // Call check every time you use the data that came from this file.
  final def tick() {
    if (isTimeToCheckFile) {
      notifyIfFileWasModified()
    }
  }

  // End user usable code
  protected def isTimeToCheckFile: Boolean = {
    if (millisSinceLastFileCheck > millisBetweenFileChecks) {
      logger.debug("Don't use cache, diff between millisSinceLastCheck %d > %d millis".format(
        millisSinceLastFileCheck, millisBetweenFileChecks
      ))
      true
    } else {
      logger.debug("Keep using the cache, cache not yet expired")
      false
    }
  }

  protected def notifyIfFileWasModified() {
    try {
      val file = FileWatcher.fileGuard(filename)
      // modify lastTimeFileChecked _after_ the guard. This will cause continued re-check of file in
      // the case that an exception is thrown (isTimeToCheckFile continues to be true)
      lastTimeFileChecked = now
      val mTime = epoch(file.lastModified)
      logger.debug("File modification time %d".format(mTime))
      if (lastModificationTime >= mTime) {
        logger.debug("Last modification time of %s has not changed, returning".format(filename))
        return
      }
      logger.info("File modification time of '%s' changed from %d to %d".format(
        filename, lastModificationTime, mTime
      ))
      lastModificationTime = mTime
      onChange(file)
    } catch {
      case e => handleFileNotFound(e)
    }
  }

  def handleFileNotFound(t: Throwable) {
    val msg = "Refusing to trigger onChange for %s, file does not exist".format(filename)
    lastModificationTime = 0L
    logger.warn(msg, t)
    onError(new File(filename))
  }

  private def millisSinceLastFileCheck: Long = {
    math.abs(lastTimeFileChecked - now)
  }
  private def epoch(seed: Long = 0): Long = if (seed <= 0) {
    new Date().getTime()
  } else {
    seed
  }
  private def now: Long = epoch(0)
}

trait FileWatcherResults[T] extends FileWatcher {
  protected val init: T
  lazy private val data = new AtomicReference[T](init)
  // This isn't used for synchronization as much as just making sure that if fromFile happens to not
  // be thread safe we don't see any weird issues
  private val lock = new ReentrantLock();

  override protected def onChange(f: File) {
    lock.isHeldByCurrentThread() match {
      case false =>
        lock.lock()
        try {
          data.set(fromFile(f))
        } finally {
          lock.unlock()
        }
      case true =>
    }
  }

  protected def fromFile(f: File): T

  def getFileContents(): T = {
    tick()
    data.get()
  }

}
