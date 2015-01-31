package util

import java.security._
import javax.crypto._
import javax.crypto.spec._
import scala.util.Random
import org.bouncycastle.crypto.PBEParametersGenerator
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.generators.PKCS12ParametersGenerator
import org.bouncycastle.crypto.modes.CBCBlockCipher
import org.bouncycastle.crypto.paddings.{PaddedBufferedBlockCipher, PKCS7Padding}
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.jce.provider.BouncyCastleProvider

import play.api.Play

trait CryptoAccessor {
  def getCryptoKey(): String
}

object CryptoCodec {
  def apply(key: String) = new CryptoCodec(key)
  private val allowedChars = Vector(
    ('a' to 'z'),
    ('A' to 'Z'),
    ('0' to '9')).flatten
  private val allowedCharsSz = allowedChars.length
  def randomString(length: Int = 12): String = {
    val chars = for (i <- 0 until length) yield allowedChars(Random.nextInt(allowedCharsSz))
    chars.mkString
  }

  def withKeyFromFramework = new CryptoCodec(getCryptoKeyFromFramework)

  protected def getCryptoKeyFromFramework(): String = {
    Play.maybeApplication.map { app =>
      app.global match {
        case c: CryptoAccessor => c.getCryptoKey()
        case _ => throw new RuntimeException("Application is not a CryptoAccessor")
      }
    }.getOrElse(throw new RuntimeException("Not in application context"))
  }

}

class CryptoCodec(privateKey: String, saltSize: Int = 8, iterations: Int = 100) {
  private val secretKey = privateKey.toArray

  protected def combiner(values: String*) = values.mkString(":")

  protected def createSalt(size: Int): Array[Byte] = {
    val salt = new Array[Byte](size)
    val saltGen = SecureRandom.getInstance("SHA1PRNG")
    saltGen.nextBytes(salt)
    salt
  }

  object Decode {
    def toUsernamePassword(value: String): Option[(String,String)] = {
      apply(value).flatMap { decoded =>
        decoded.split(":", 2).toList match {
          case username :: password :: Nil =>
            Some((username, password))
          case _ => None
        }
      }
    }
    protected def splitWithSalt(value: String): (Array[Byte], Array[Byte]) = {
      val hex = Hex.fromHexString(value)
      val splitAt = hex.length - saltSize
      hex.splitAt(splitAt)
    }
    def apply(value: String): Option[String] = {
      try {
        val (cipher,salt) = splitWithSalt(value)
        val pGen = new PKCS12ParametersGenerator(new SHA256Digest())
        val pkcs12PasswordBytes = PBEParametersGenerator.PKCS12PasswordToBytes(secretKey)
        pGen.init(pkcs12PasswordBytes, salt, iterations)
        val aesCBC = new CBCBlockCipher(new AESEngine())
        val aesCBCParams = pGen.generateDerivedParameters(256, 128).asInstanceOf[ParametersWithIV]
        aesCBC.init(false, aesCBCParams)
        val aesCipher = new PaddedBufferedBlockCipher(aesCBC, new PKCS7Padding())
        val plainTemp = new Array[Byte](aesCipher.getOutputSize(cipher.length))
        val offset = aesCipher.processBytes(cipher, 0, cipher.length, plainTemp, 0)
        val last = aesCipher.doFinal(plainTemp, offset)
        val plain = new Array[Byte](offset + last)
        Array.copy(plainTemp, 0, plain, 0, plain.length)
        Some(new String(plain))
      } catch {
        case _: Throwable => None
      }
    }
  }
  object Encode {
    private val encodeType = "PBEWithSHA256And256BitAES-CBC-BC"
    def apply(values: String*): String = {
      apply(combiner(values:_*).getBytes)
    }
    def apply(value: Array[Byte]): String = {
      val salt = createSalt(saltSize)
      Security.addProvider(new BouncyCastleProvider())
      val pbeParamSpec = new PBEParameterSpec(salt, iterations)
      val pbeKeySpec = new PBEKeySpec(secretKey)
      val keyFac = SecretKeyFactory.getInstance(encodeType)
      val pbeKey = keyFac.generateSecret(pbeKeySpec)
      val encryptionCipher = Cipher.getInstance(encodeType)
      encryptionCipher.init(Cipher.ENCRYPT_MODE, pbeKey, pbeParamSpec)
      Hex.toHexString(Array(encryptionCipher.doFinal(value), salt).flatten)
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
