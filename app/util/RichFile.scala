package util

import java.io.File

// Borrowed from http://rosettacode.org/wiki/Walk_a_directory/Recursively#Scala

/**
 * A wrapper around File, allowing iteration either on direct directory children
 * or recursively against a directory tree.
 */
class RichFile(file: File) {

  def children = new Iterable[File] {
    def iterator = if (file.isDirectory) file.listFiles.iterator else Iterator.empty
  }

  def andTree : Iterable[File] = 
    Seq(file) ++ children.flatMap(child => new RichFile(child).andTree)

}
 
/** Implicitely enrich java.io.File with methods of RichFile */
object RichFile {
  implicit def toRichFile(file: File) = new RichFile(file)
}
