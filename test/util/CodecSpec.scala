package util

import org.specs2.mutable._

object CodecSpec extends Specification {
  val username = "host-xyz123456-1937-1955-1394.dfw01.tumblr.net"
  val password = "hello:world:foofa:blahh:"

  val cryptoCodec = CryptoCodec(CryptoCodec.randomString(23))

  "The Crypto Codec" should {
    "generate random strings" >> {
      val passwords = (1 to 100).map { _ => CryptoCodec.randomString(14) }
      passwords must have size(100)
      ((_:String) must have size(14)).forall(passwords)
      passwords.distinct must have size(100)
    }
    "encode" >> {
      val message = "Hello World"
      val array = message.getBytes("UTF-8")
      val encrypted = cryptoCodec.Encode(array)
      encrypted must haveClass[String]
      encrypted must not be empty
    }
    "encode usernames/passwords" >> {
      val encrypted = cryptoCodec.Encode(username, password)
      encrypted must haveClass[String]
      encrypted must not be empty
    }
    "not encode the same thing the same way" >> {
      val encrypted = cryptoCodec.Encode(username, password)
      val encrypted2 = cryptoCodec.Encode(username, password)
      encrypted must not be equalTo(encrypted2)
    }
    "decode" >> {
      val message = "Hello World"
      val array = message.getBytes("UTF-8")
      val encrypted = cryptoCodec.Encode(array)
      val decrypted = cryptoCodec.Decode(encrypted)
      decrypted must beSome(message)
    }
    "decode stored" >> {
      val key = "jLloDCPFCPaSZmG6" // Generated with CryptoCodec.randomString(16)
      val codec = CryptoCodec(key)
      val encrypted = "dab465c6a24fb8ac82c8f03c7623a6b8e5827a5d2baca8f7" // Encrypted with key
      val decrypted = codec.Decode(encrypted)
      decrypted must beSome("Hello World")
    }
    "decode usernames/passwords" >> {
      val message = "Hello World"
      val array = message.getBytes("UTF-8")
      val encrypted = cryptoCodec.Encode(username, password)
      val decrypted = cryptoCodec.Decode.toUsernamePassword(encrypted)
      decrypted must beSome
      decrypted.get must beEqualTo((username, password))
    }
    "decode same data (different encoding)" >> {
      val encrypted = cryptoCodec.Encode(username, password)
      val encrypted2 = cryptoCodec.Encode(username, password)
      encrypted must not be equalTo(encrypted2)
      val decrypted = cryptoCodec.Decode.toUsernamePassword(encrypted)
      val decrypted2 = cryptoCodec.Decode.toUsernamePassword(encrypted2)
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
