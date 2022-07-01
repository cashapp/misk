package misk.cloud.gcp.security.encryption

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.KmsEnvelopeAeadKeyManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealEnvelopeEncryptor @Inject constructor(
  private val config: EnvelopeEncryptionConfig,
) : EnvelopeEncryptor {
  override fun encrypt(payloadToEncrypt: ByteArray): ByteArray {
    val dekHandle = KeysetHandle.generateNew(
      KmsEnvelopeAeadKeyManager.createKeyTemplate(
        config.kekUri,
        KeyTemplates.get(DEK_TEMPLATE)
      )
    )
    val dekAead = dekHandle.getPrimitive(Aead::class.java)

    return dekAead.encrypt(payloadToEncrypt, byteArrayOf())
  }

  companion object {
    const val DEK_TEMPLATE = "AES256_GCM"
  }
}
