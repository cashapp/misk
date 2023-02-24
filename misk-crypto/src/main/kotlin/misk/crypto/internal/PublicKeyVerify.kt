package misk.crypto.internal

class PublicKeyVerify(
  private val key: misk.crypto.Key,
  private val verify: com.google.crypto.tink.PublicKeyVerify,
  private val metrics: KeyMetrics):  com.google.crypto.tink.PublicKeyVerify by verify {

  override fun verify(signature: ByteArray?, data: ByteArray?) {
    return verify.verify(signature, data).also {
      metrics.verified(key, data?.size ?: 0)
    }
  }

}
