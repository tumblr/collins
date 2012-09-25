package collins.validation

import java.io.{File => IoFile}

object File {
  def requireDirIsReadable(dirname: String) {
    require(isFileReadable(dirname), "Directory %s does not exist or can not be read".format(dirname))
  }
  def requireDirIsReadable(dir: IoFile) {
    require(isFileReadable(dir), "Directory %s does not exist or can not be read".format(dir.toString))
  }
  def requireFileIsReadable(filename: String) {
    require(isFileReadable(filename), "File %s does not exist or can not be read".format(filename))
  }
  def requireFileIsReadable(file: IoFile) {
    require(isFileReadable(file), "File %s does not exist or can not be read".format(file.toString))
  }

  def isFileReadable(filename: String): Boolean =
    isFileReadable(new IoFile(filename))
  def isFileReadable(file: IoFile): Boolean =
    file.exists() && file.isFile() && file.canRead()
  def isDirReadable(dirname: String): Boolean =
    isDirReadable(new IoFile(dirname))
  def isDirReadable(dir: IoFile): Boolean =
    dir.exists() && dir.isDirectory() && dir.canRead()
}
