package collins

trait ResourceFinder {
  def findResource(filename: String) = {
    import java.io.File
    val url = getClass.getClassLoader.getResource(filename)
    new File(url.getFile)
  }
  def getResource(filename: String) = {
    val stream = getClass.getClassLoader.getResourceAsStream(filename)
    val tmp = io.Source.fromInputStream(stream)
    val str = tmp.mkString
    tmp.close()
    str
  }
}
