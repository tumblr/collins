package util

import org.specs2.mutable._

object CodecSpec extends Specification {
  val username = "blake"
  val password = "hello:world"

  "The Crypto Codec" should {
    "generate random strings" >> {
      val passwords = (1 to 100).map { _ => CryptoCodec.randomString(13) }
      passwords must have size(100)
      passwords.foreach { _ must have size(13) }
      passwords.distinct must have size(100)
    }
    "encode" >> {
      val message = "Hello World"
      val array = message.getBytes("UTF-8")
      val encrypted = CryptoCodec.Encode(array)
      encrypted must haveClass[String]
      encrypted must not be empty
    }
    "encode usernames/passwords" >> {
      val encrypted = CryptoCodec.Encode(username, password)
      encrypted must haveClass[String]
      encrypted must not be empty
    }
    "not encode the same thing the same way" >> {
      val encrypted = CryptoCodec.Encode(username, password)
      val encrypted2 = CryptoCodec.Encode(username, password)
      encrypted must not be equalTo(encrypted2)
    }
    "decode" >> {
      val message = "Hello World"
      val array = message.getBytes("UTF-8")
      val encrypted = CryptoCodec.Encode(array)
      val decrypted = CryptoCodec.Decode(encrypted)
      decrypted must beSome(message)
    }
    "decode usernames/passwords" >> {
      val message = "Hello World"
      val array = message.getBytes("UTF-8")
      val encrypted = CryptoCodec.Encode(username, password)
      val decrypted = CryptoCodec.Decode.toUsernamePassword(encrypted)
      decrypted must beSome
      decrypted.get must beEqualTo((username, password))
    }
    "decode same data (different encoding)" >> {
      val encrypted = CryptoCodec.Encode(username, password)
      val encrypted2 = CryptoCodec.Encode(username, password)
      encrypted must not be equalTo(encrypted2)
      val decrypted = CryptoCodec.Decode.toUsernamePassword(encrypted)
      val decrypted2 = CryptoCodec.Decode.toUsernamePassword(encrypted2)
      decrypted must beSome
      decrypted2 must beSome
      decrypted.get must beEqualTo((username, password))
      decrypted2.get must beEqualTo((username, password))
      decrypted must be equalTo(decrypted2)
    }
  }

  "The Hex Codec" should {
    "encode" >> {
      val message = "Hello World"
      val messageArray = message.getBytes("UTF-8")
      val hex = Hex.toHexString(messageArray)
      hex must haveClass[String]
      hex must not be empty
    }
    "decode" >> {
      val message = "48656c6c6f20576f726c64" // Hello World
      val hex = Hex.fromHexString(message)
      hex must not be empty
      new String(hex) must beEqualTo("Hello World")
    }
  }
}
