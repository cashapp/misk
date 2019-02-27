package misk.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadFactory
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * An even simpler interface than Tink's [Aead].
 * This also makes it easier to mock Ciphers for testing purposes.
 */
interface Cipher {
  /**
   * Encrypt the given data.
   * If anything goes wrong, throws a [java.security.GeneralSecurityException]
   */
  fun encrypt(plaintext: ByteString): ByteString

  /**
   * Decrypt the given data
   * If anything goes wrong, including failing to decipher the data,
   * throws a [java.security.GeneralSecurityException]
   */
  fun decrypt(ciphertext: ByteString): ByteString

  /**
   * Returns information about this [Cipher] instance.
   * This includes the algorithm type, key identifiers, primary identifier
   * and a Base64 encoded and encrypted key
   */
  val keyInfo: KeyInfo
}

/**
 * Real implementation of the [Cipher] interface.
 * This class uses key [KeysetHandle] that's been decrypted by the KMS when the module was loaded.
 */
class RealCipher internal constructor(
  private val keysetHandle: KeysetHandle,
  private val masterKey: Aead
) : Cipher {

  private val aead = AeadFactory.getPrimitive(keysetHandle)

  override fun encrypt(plaintext: ByteString): ByteString {
    val encrypted = aead.encrypt(plaintext.toByteArray(), null)
    return encrypted.toByteString()
  }

  override fun decrypt(ciphertext: ByteString): ByteString {
    val decrypted = aead.decrypt(ciphertext.toByteArray(), null)
    return decrypted.toByteString()
  }

  override val keyInfo: KeyInfo
    get() {
      val stream = ByteArrayOutputStream()
      val keyWriter = JsonKeysetWriter.withOutputStream(stream)
      keysetHandle.write(keyWriter, masterKey)
      val encryptedKey = Base64.getEncoder().encodeToString(stream.toByteArray())
      return KeyInfo(keysetHandle.keysetInfo.toString(), encryptedKey)
    }
}

data class KeyInfo(val tinkInfo: String, val encryptedKey: String)