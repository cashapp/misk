package misk.crypto.internal

import com.google.crypto.tink.PublicKeySign

class PublicKeySign(
  private val key: misk.crypto.Key,
  private val sign: com.google.crypto.tink.PublicKeySign,
  private val metrics: KeyMetrics,
  ): com.google.crypto.tink.PublicKeySign by sign {

  override fun sign(data: ByteArray?): ByteArray {
    return sign.sign(data).also {
      metrics.signed(key, data?.size ?: 0)
    }
  }

}
