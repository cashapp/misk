package misk.crypto.internal

import com.google.crypto.tink.Aead

/**
 * Decorates {@link com.google.crypto.tink.Aead} to collect metrics on operations.
 */
class Aead(
  private val key: misk.crypto.Key,
  private val aead: com.google.crypto.tink.Aead,
  private val metrics: KeyMetrics,
) : Aead by aead {

  override fun encrypt(plaintext: ByteArray?, associatedData: ByteArray?): ByteArray {
    return aead.encrypt(plaintext, associatedData).also {
      metrics.encrypted(key, plaintext?.size ?: 0)
    }
  }

  override fun decrypt(ciphertext: ByteArray?, associatedData: ByteArray?): ByteArray {
    return aead.decrypt(ciphertext, associatedData).also {
      metrics.decrypted(key, it.size)
    }
  }

}
