package util

import java.security._
import javax.crypto._
import javax.crypto.spec._

object CryptoCodec {
  private val kgen = KeyGenerator.getInstance("AES")
  kgen.init(256)
  val keySize = 256/8 // 256 bit AES key is 32 bytes

  protected def combiner(values: String*) = values.mkString(":")
  protected def splitter(value: String) = value.split(":")

  object Decode {
    def toUsernamePassword(value: String): Option[(String,String)] = {
      apply(value).flatMap { decoded =>
        decoded.split(":").toList match {
          case Nil =>
            None
          case head :: Nil =>
            None
          case first :: last =>
            Some((first, last.mkString(":")))
        }
      }
    }
    def apply(value: String): Option[String] = {
      try {
        val hex = Hex.fromHexString(value)
        val splitAt = hex.length - keySize
        val (orig,salt) = hex.splitAt(splitAt)
        val cipher = Cipher.getInstance("AES")
        val skeySpec = new SecretKeySpec(salt, "AES")
        cipher.init(Cipher.DECRYPT_MODE, skeySpec)
        Some(new String(cipher.doFinal(orig)))
      } catch {
        case _ => None
      }
    }
  }
  object Encode {
    def apply(values: String*): String = {
      apply(combiner(values:_*).getBytes)
    }
    def apply(value: Array[Byte]): String = {
      val skey = kgen.generateKey()
      val raw = skey.getEncoded()
      val skeySpec = new SecretKeySpec(raw, "AES")
      val cipher = Cipher.getInstance("AES")
      cipher.init(Cipher.ENCRYPT_MODE, skeySpec)
      val encrypted = cipher.doFinal(value)
      val salted = Array(encrypted, raw).flatten
      Hex.toHexString(salted)
    }
  }
}

object Hex {
  import org.apache.commons.codec.binary.Hex
  def hex = new Hex()
  def toHexString(value: Array[Byte]): String = {
    new String(hex.encode(value))
  }
  def fromHexString(value: String): Array[Byte] = {
    hex.decode(value.getBytes("UTF-8"))
  }
}


