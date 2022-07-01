package misk.cloud.gcp.security.encryption

import com.google.crypto.tink.Aead
import com.google.crypto.tink.BinaryKeysetWriter
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeEnvelopeEncryptor @Inject constructor() : EnvelopeEncryptor {
  private val kekAead =
    KeysetHandle.generateNew(KeyTemplates.get(KEY_TEMPLATE)).getPrimitive(Aead::class.java)

  override fun encrypt(payloadToEncrypt: ByteArray): ByteArray {
    val dekHandle = KeysetHandle.generateNew(KeyTemplates.get(KEY_TEMPLATE))
    val dekAead = dekHandle.getPrimitive(Aead::class.java)

    val encryptedDek = encryptDek(dekHandle, kekAead)
    val encryptedPayload = dekAead.encrypt(payloadToEncrypt, byteArrayOf())

    return ByteBuffer.allocate(4 + encryptedDek.size + encryptedPayload.size)
      .putInt(encryptedDek.size)
      .put(encryptedDek)
      .put(encryptedPayload)
      .array()
  }

  private fun encryptDek(dek: KeysetHandle, kek: Aead): ByteArray {
    val encryptedDekStream = ByteArrayOutputStream()
    dek.write(BinaryKeysetWriter.withOutputStream(encryptedDekStream), kek)
    return encryptedDekStream.toByteArray()
  }

  companion object {
    const val KEY_TEMPLATE = "AES256_GCM"
  }
}
