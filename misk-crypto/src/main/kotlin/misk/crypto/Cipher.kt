package misk.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadFactory
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import java.util.Base64

/**
 * An even simpler interface than Tink's [Aead].
 * This also makes it easier to mock Ciphers for testing purposes.
 */
interface Cipher {
  /**
   * Encrypt the given data.
   */
  fun encrypt(plaintext: ByteString): ByteString

  /**
   * Decrypt the given data.
   * If anything goes wrong, including failing to decipher the data,
   * throws a [java.security.GeneralSecurityException]
   * and [NullPointerException] if no suitable key was found to decrypt the data.
   */
  fun decrypt(ciphertext: ByteString): ByteString

  /**
   * Returns information about this [Cipher] instance.
   * This includes the algorithm type, key identifiers, primary identifier
   * and a Base64 encoded and encrypted key
   */
  val keyInfo: List<KeyInfo>
}

/**
 * Real implementation of the [Cipher] interface.
 * This class uses key [KeysetHandle] that's been decrypted by the KMS when the module was loaded.
 */
class RealCipher internal constructor(
  private val keys: List<Pair<KeysetHandle, Aead>>
) : Cipher {

  private val keysets = keys.map { it.first }

  override fun encrypt(plaintext: ByteString): ByteString {
    // .toByteArray() creates a copy of the receiver ByteString.
    val plaintextBytes = plaintext.toByteArray()
    val aead = AeadFactory.getPrimitive(keysets.first())
    val encrypted = aead.encrypt(plaintextBytes, null)
    // We want to make sure this code doesn't leave behind any unnecessary copies of the plaintext.
    // So before retuning, make sure we override the extra copy of the plaintext with 0's.
    plaintextBytes.fill(0)
    return encrypted.toByteString()
  }

  override fun decrypt(ciphertext: ByteString): ByteString {
    var decryptedBytes: ByteArray?
    var decrypted: ByteString? = null
    for (keyset in keysets) {
      try {
        val aead = AeadFactory.getPrimitive(keyset)
        decryptedBytes = aead.decrypt(ciphertext.toByteArray(), null)
        // .toByteString() creates a copy of the receiver object
        // Make sure we don't leave any unnecessary copies of the decrypted data laying around.
        decrypted = decryptedBytes.toByteString()
        decryptedBytes.fill(0)
      } catch (e: GeneralSecurityException) {
      }
    }
    return decrypted!!
  }

  override val keyInfo: List<KeyInfo>
    get() {
      return keys.map { (keysetHandle, masterKey) ->
        val stream = ByteArrayOutputStream()
        val keyWriter = JsonKeysetWriter.withOutputStream(stream)
        keysetHandle.write(keyWriter, masterKey)
        val encryptedKey = Base64.getEncoder().encodeToString(stream.toByteArray())
        KeyInfo(keysetHandle.keysetInfo.toString(), encryptedKey)
      }
    }
}

data class KeyInfo(
  val tinkInfo: String,
  val encryptedKey: String
)