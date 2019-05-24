package misk.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.KmsClient
import com.google.crypto.tink.Mac
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.PublicKeyVerify
import com.google.crypto.tink.mac.MacFactory
import com.google.crypto.tink.proto.KeyTemplate
import com.google.crypto.tink.signature.PublicKeySignFactory
import com.google.crypto.tink.signature.PublicKeyVerifyFactory
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.KeysetReader
import com.google.crypto.tink.aead.AeadFactory
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.aead.KmsEnvelopeAead
import com.google.crypto.tink.daead.DeterministicAeadFactory
import com.google.inject.Inject
import com.google.inject.Provider
import misk.environment.Environment
import misk.logging.getLogger
import java.security.GeneralSecurityException

val logger by lazy { getLogger<CryptoModule>() }

open class KeyReader {
  @Inject lateinit var env: Environment

  private fun readCleartextKey(reader: KeysetReader): KeysetHandle {
    if (env != Environment.TESTING && env != Environment.DEVELOPMENT)
      throw GeneralSecurityException(
          "Trying to use a plaintext key outside of a development environment")

    logger.warn { "Reading a plaintext key" }
    return CleartextKeysetHandle.read(reader)
  }

  private fun readEncryptedKey(reader: KeysetReader, kmsUri: String, client: KmsClient): KeysetHandle {
    val masterKey = client.getAead(kmsUri)
    return KeysetHandle.read(reader, masterKey)
  }

  fun readKey(key: Key, kmsUri: String?, kmsClient: KmsClient): KeysetHandle {
    val keyJson = JsonKeysetReader.withString(key.encrypted_key.value)

    return if (kmsUri != null) {
      readEncryptedKey(keyJson, kmsUri, kmsClient)
    } else {
      readCleartextKey(keyJson)
    }
  }
}

/**
 * We only support AEAD keys via envelope encryption.
 */
internal class AeadEnvelopeProvider(val key: Key, val kmsUri: String?) : Provider<Aead>,
    KeyReader() {
  @Inject lateinit var keyManager: AeadKeyManager
  @Inject lateinit var kmsClient: KmsClient

  override fun get(): Aead {
    val keysetHandle = readKey(key, kmsUri, kmsClient)
    val kek = AeadFactory.getPrimitive(keysetHandle)
    val envelopeKey = KmsEnvelopeAead(DEK_TEMPLATE, kek)

    return envelopeKey.also { keyManager[key.key_name] = it }
  }

  companion object {
    val DEK_TEMPLATE: KeyTemplate = AeadKeyTemplates.AES128_GCM
  }
}

internal class DeterministicAeadProvider(
  val key: Key,
  val kmsUri: String?
) : Provider<DeterministicAead>, KeyReader() {
  @Inject lateinit var keyManager: DeterministicAeadKeyManager
  @Inject lateinit var kmsClient: KmsClient

  override fun get(): DeterministicAead {
    val keysetHandle = readKey(key, kmsUri, kmsClient)
    val daeadKey = DeterministicAeadFactory.getPrimitive(keysetHandle)

    return daeadKey.also { keyManager[key.key_name] = it }
  }
}

internal class MacProvider(val key: Key, val kmsUri: String?) : Provider<Mac>, KeyReader() {
  @Inject lateinit var keyManager: MacKeyManager
  @Inject lateinit var kmsClient: KmsClient

  override fun get(): Mac {
    val keysetHandle = readKey(key, kmsUri, kmsClient)
    return MacFactory.getPrimitive(keysetHandle)
        .also { keyManager[key.key_name] = it }
  }
}

internal class DigitalSignatureSignerProvider(
  val key: Key,
  val kmsUri: String?
) : Provider<PublicKeySign>, KeyReader() {
  @Inject lateinit var keyManager: DigitalSignatureKeyManager
  @Inject lateinit var kmsClient: KmsClient

  override fun get(): PublicKeySign {
    val keysetHandle = readKey(key, kmsUri, kmsClient)
    val signer = PublicKeySignFactory.getPrimitive(keysetHandle)
    val verifier = PublicKeyVerifyFactory.getPrimitive(keysetHandle.publicKeysetHandle)
    keyManager[key.key_name] = DigitalSignature(signer, verifier)
    return signer
  }
}

internal class DigitalSignatureVerifierProvider(
  val key: Key,
  val kmsUri: String?
) : Provider<PublicKeyVerify>, KeyReader() {
  @Inject lateinit var keyManager: DigitalSignatureKeyManager
  @Inject lateinit var kmsClient: KmsClient

  override fun get(): PublicKeyVerify {
    val keysetHandle = readKey(key, kmsUri, kmsClient)
    val signer = PublicKeySignFactory.getPrimitive(keysetHandle)
    val verifier = PublicKeyVerifyFactory.getPrimitive(keysetHandle.publicKeysetHandle)
    keyManager[key.key_name] = DigitalSignature(signer, verifier)
    return verifier
  }
}

