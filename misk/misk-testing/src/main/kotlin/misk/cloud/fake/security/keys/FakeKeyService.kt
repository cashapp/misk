package misk.cloud.fake.security.keys

import misk.security.keys.KeyService
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.nio.ByteBuffer
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class FakeKeyService : KeyService {
  override fun encrypt(keyAlias: String, plainText: ByteString): ByteString {
    val key = newSymmetricKey(keyAlias)
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    return ByteBuffer.wrap(cipher.doFinal(plainText.toByteArray())).toByteString()
  }

  override fun decrypt(keyAlias: String, cipherText: ByteString): ByteString {
    val key = newSymmetricKey(keyAlias)
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.DECRYPT_MODE, key)
    return ByteBuffer.wrap(cipher.doFinal(cipherText.toByteArray())).toByteString()
  }

  private fun newSymmetricKey(keyText: String): Key {
    val keyInput = when (keyText.length) {
      in 1..31 -> keyText + " ".repeat(32 - keyText.length)
      32 -> keyText
      else -> keyText.take(32)
    }
    return SecretKeySpec(keyInput.toByteArray(Charsets.US_ASCII), "AES")
  }
}
