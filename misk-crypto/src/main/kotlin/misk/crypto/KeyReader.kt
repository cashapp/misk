package misk.crypto

import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.KmsClient
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.aead.KmsEnvelopeAead
import com.google.crypto.tink.proto.KeyTemplate
import com.google.inject.Inject
import wisp.logging.getLogger
import java.security.GeneralSecurityException

open class KeyReader {
  companion object {
    val KEK_TEMPLATE: KeyTemplate = AeadKeyTemplates.AES256_GCM
  }

  @Inject lateinit var kmsClient: KmsClient

  @Inject lateinit var keySources: Set<KeyResolver>

  private val logger = getLogger<KeyReader>()

  private fun readCleartextKey(key: Key): KeysetHandle {
    // TODO: Implement a clean check to throw if we are running in prod or staging. Checking for
    // an injected Environment will fail if a test explicitly creates a staging/prod environment.
    logger.warn { "reading a plaintext key!" }
    val reader = JsonKeysetReader.withString(key.encrypted_key!!.value)
    return CleartextKeysetHandle.read(reader)
  }

  private fun readEncryptedKey(key: Key): KeysetHandle {
    val masterKey = kmsClient.getAead(key.kms_uri)
    return try {
      val kek = KmsEnvelopeAead(KEK_TEMPLATE, masterKey)
      val reader = JsonKeysetReader.withString(key.encrypted_key!!.value)
      KeysetHandle.read(reader, kek)
    } catch (ex: GeneralSecurityException) {
      logger.warn { "using obsolete key format, rotate your keys when possible" }
      val reader = JsonKeysetReader.withString(key.encrypted_key!!.value)
      KeysetHandle.read(reader, masterKey)
    }
  }

  fun readKey(alias: KeyAlias): KeysetHandle {
    val key = getRawKey(alias)
    return if (key.kms_uri != null) {
      readEncryptedKey(key)
    } else {
      readCleartextKey(key)
    }
  }

  protected fun getRawKey(alias: KeyAlias): Key {
    return keySources.mapNotNull { it.getKeyByAlias(alias) }.first()
  }
}
