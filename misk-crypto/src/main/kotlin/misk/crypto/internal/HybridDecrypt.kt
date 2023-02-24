package misk.crypto.internal

class HybridDecrypt(
  private val key: misk.crypto.Key,
  private val decrypt: com.google.crypto.tink.HybridDecrypt,
  private val metrics: KeyMetrics
) : com.google.crypto.tink.HybridDecrypt by decrypt {

  override fun decrypt(ciphertext: ByteArray?, contextInfo: ByteArray?): ByteArray {
    return decrypt.decrypt(ciphertext, contextInfo).also {
      metrics.decrypted(key, it?.size ?: 0)
    }
  }

}
