package misk.crypto.internal

import com.google.crypto.tink.DeterministicAead

/**
 * Decorates {@link com.google.crypto.tink.DeterministicAead} to collect metrics on operations.
 */
class DeterministicAead(
  private val key: misk.crypto.Key,
  private val aead: com.google.crypto.tink.DeterministicAead,
  private val metrics: KeyMetrics,
) : DeterministicAead by aead {

  override fun encryptDeterministically(
    plaintext: ByteArray?,
    associatedData: ByteArray?
  ): ByteArray {
    return aead.encryptDeterministically(plaintext, associatedData).also {
      metrics.encrypted(key, plaintext?.size ?: 0)
    }
  }

  override fun decryptDeterministically(
    ciphertext: ByteArray?,
    associatedData: ByteArray?
  ): ByteArray {
    return aead.decryptDeterministically(ciphertext, associatedData).also {
      metrics.decrypted(key, it.size)
    }
  }

}
