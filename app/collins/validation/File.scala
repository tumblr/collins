package collins.validation

import java.io.{File => IoFile}

object File {
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
}
