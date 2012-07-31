package test

import java.io._
import scala.util.Random

object FileHelper {
  def createRandomFile(content: Option[String] = None): File = {
    val f = File.createTempFile("collins_test_FileHelper", null)
    f.deleteOnExit()
    content.foreach(c => append(f, c))
    f
  }
  def createRandomFile[A](content: String)(f: File => A): A = {
    val rando = createRandomFile(Some(content))
    val r = f(rando)
    unlinkFile(rando)
    r
  }
  def createThenUnlink: File = {
    val f = createRandomFile(None)
    unlinkFile(f)
    f
  }
  def append(file: File, content: String) {
    if (!file.exists) {
      file.createNewFile()
    }
    val fos = new FileOutputStream(file, true)
    val bytes = (content + "\n").getBytes
    fos.write(bytes)
    fos.flush()
    fos.getFD().sync()
    fos.close()
  }
  def unlinkFile(file: File): Boolean = {
    file.delete()
  }
  def apply(filename: String) = new File(filename)
  def lastModified(file: File): Long = file.lastModified
  def lastModified(filename: String): Long = lastModified(new File(filename))
  def randomContent(bytes: Int): String = {
    for (c <- 0 until bytes) yield Random.nextPrintableChar()
  }.mkString
}
