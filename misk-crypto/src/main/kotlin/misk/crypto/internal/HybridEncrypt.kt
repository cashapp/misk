package misk.crypto.internal

class HybridEncrypt(
  private val key: misk.crypto.Key,
  private val encrypt: com.google.crypto.tink.HybridEncrypt,
  private val metrics: KeyMetrics
) : com.google.crypto.tink.HybridEncrypt by encrypt {

  override fun encrypt(plaintext: ByteArray?, contextInfo: ByteArray?): ByteArray {
    return encrypt.encrypt(plaintext, contextInfo).also {
      metrics.encrypted(key, plaintext?.size ?: 0)
    }
  }

}
