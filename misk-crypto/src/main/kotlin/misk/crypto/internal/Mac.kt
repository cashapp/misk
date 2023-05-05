package misk.crypto.internal

import com.google.crypto.tink.Mac

/**
 * Decorates {@link com.google.crypto.tink.Aead} to collect metrics on operations.
 */
class Mac(
  private val key: misk.crypto.Key,
  private val mac: com.google.crypto.tink.Mac,
  private val metrics: KeyMetrics,
) : Mac by mac {

  override fun computeMac(data: ByteArray?): ByteArray {
    return mac.computeMac(data).also {
      metrics.encrypted(key, data?.size ?: 0)
    }
  }

  override fun verifyMac(inMac: ByteArray?, data: ByteArray?) {
    return mac.verifyMac(inMac, data).also {
      metrics.verified(key, data?.size ?: 0)
    }
  }

}
