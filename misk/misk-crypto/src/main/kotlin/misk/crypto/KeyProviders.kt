package misk.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.CleartextKeysetHandle
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
import com.google.crypto.tink.aead.AeadFactory
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.aead.KmsEnvelopeAead
import com.google.inject.Inject
import com.google.inject.Provider
import misk.logging.getLogger

val logger by lazy { getLogger<CryptoModule>() }

/**
 * We only support AEAD keys via envelope encryption.
 */
internal class AeadEnvelopeProvider(val key: Key, val kmsUri: String?) : Provider<Aead> {
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

internal class MacProvider(val key: Key, val kmsUri: String?) : Provider<Mac> {
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
) : Provider<PublicKeySign> {
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
) : Provider<PublicKeyVerify> {
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

private fun readKey(key: Key, kmsUri: String?, kmsClient: KmsClient): KeysetHandle {
  val keyJson = JsonKeysetReader.withString(key.encrypted_key.value)

  return if (kmsUri != null) {
    val masterKey = kmsClient.getAead(kmsUri)
    KeysetHandle.read(keyJson, masterKey)
  } else {
    logger.warn { "Reading a plaintext key" }
    CleartextKeysetHandle.read(keyJson)
  }
}

