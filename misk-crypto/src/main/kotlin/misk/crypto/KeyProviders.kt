package misk.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
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
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.aead.AeadFactory
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.aead.KmsEnvelopeAead
import com.google.crypto.tink.daead.DeterministicAeadFactory
import com.google.crypto.tink.hybrid.HybridDecryptFactory
import com.google.crypto.tink.hybrid.HybridEncryptFactory
import com.google.crypto.tink.streamingaead.StreamingAeadFactory
import com.google.inject.Inject
import com.google.inject.Provider
import misk.logging.getLogger
import java.security.GeneralSecurityException

open class KeyReader {
  companion object {
    val KEK_TEMPLATE: KeyTemplate = AeadKeyTemplates.AES256_GCM
  }

  @Inject lateinit var kmsClient: KmsClient

  @Inject lateinit var keySources: Set<ExternalKeyManager>

  private val logger by lazy { getLogger<KeyReader>() }

  private fun readCleartextKey(key: Key): KeysetHandle {
    // TODO: Implement a clean check to throw if we are running in prod or staging. Checking for
    // an injected Environment will fail if a test explicitly creates a staging/prod environment.
    logger.warn { "reading a plaintext key!" }
    val reader = JsonKeysetReader.withString(key.encrypted_key.value)
    return CleartextKeysetHandle.read(reader)
  }

  private fun readEncryptedKey(key: Key): KeysetHandle {
    val masterKey = kmsClient.getAead(key.kms_uri)
    return try {
      val kek = KmsEnvelopeAead(KEK_TEMPLATE, masterKey)
      val reader = JsonKeysetReader.withString(key.encrypted_key.value)
      KeysetHandle.read(reader, kek)
    } catch (ex: GeneralSecurityException) {
      logger.warn { "using obsolete key format, rotate your keys when possible" }
      val reader = JsonKeysetReader.withString(key.encrypted_key.value)
      KeysetHandle.read(reader, masterKey)
    }
  }

  fun readKey(alias: KeyAlias): KeysetHandle {
    val key = keySources.mapNotNull { it.getKeyByAlias(alias) }.first()
    return if (key.kms_uri != null) {
      readEncryptedKey(key)
    } else {
      readCleartextKey(key)
    }
  }
}

/**
 * We only support AEAD keys via envelope encryption.
 */
internal class AeadEnvelopeProvider(
  val key: KeyAlias,
) : Provider<Aead>, KeyReader() {
  @Inject lateinit var keyManager: AeadKeyManager

  override fun get(): Aead {
    val keysetHandle = readKey(key)
    val aeadKey = AeadFactory.getPrimitive(keysetHandle)

    return aeadKey.also { keyManager[key] = it }
  }
}

internal class DeterministicAeadProvider(
  val key: KeyAlias,
) : Provider<DeterministicAead>, KeyReader() {
  @Inject lateinit var keyManager: DeterministicAeadKeyManager

  override fun get(): DeterministicAead {
    val keysetHandle = readKey(key)
    val daeadKey = DeterministicAeadFactory.getPrimitive(keysetHandle)

    return daeadKey.also { keyManager[key] = it }
  }
}

internal class MacProvider(
  val key: KeyAlias,
) : Provider<Mac>, KeyReader() {
  @Inject lateinit var keyManager: MacKeyManager

  override fun get(): Mac {
    val keysetHandle = readKey(key)
    return MacFactory.getPrimitive(keysetHandle)
        .also { keyManager[key] = it }
  }
}

internal class DigitalSignatureSignerProvider(
  val key: KeyAlias,
) : Provider<PublicKeySign>, KeyReader() {
  @Inject lateinit var keyManager: DigitalSignatureKeyManager

  override fun get(): PublicKeySign {
    val keysetHandle = readKey(key)
    val signer = PublicKeySignFactory.getPrimitive(keysetHandle)
    val verifier = PublicKeyVerifyFactory.getPrimitive(keysetHandle.publicKeysetHandle)
    keyManager[key] = DigitalSignature(signer, verifier)
    return signer
  }
}

internal class DigitalSignatureVerifierProvider(
  val key: KeyAlias,
) : Provider<PublicKeyVerify>, KeyReader() {
  @Inject lateinit var keyManager: DigitalSignatureKeyManager

  override fun get(): PublicKeyVerify {
    val keysetHandle = readKey(key)
    val signer = PublicKeySignFactory.getPrimitive(keysetHandle)
    val verifier = PublicKeyVerifyFactory.getPrimitive(keysetHandle.publicKeysetHandle)
    keyManager[key] = DigitalSignature(signer, verifier)
    return verifier
  }
}

internal class HybridEncryptProvider(
  val key: KeyAlias,
) : Provider<HybridEncrypt>, KeyReader() {
  @Inject lateinit var keyManager: HybridEncryptKeyManager

  override fun get(): HybridEncrypt {
    val keysetHandle = readKey(key)
    val publicKeysetHandle = try {
      keysetHandle.publicKeysetHandle
    } catch (e: GeneralSecurityException) {
      keysetHandle
    }
    return HybridEncryptFactory.getPrimitive(publicKeysetHandle)
        .also {keyManager[key] = it }
  }
}

internal class HybridDecryptProvider(
  val key: KeyAlias,
) : Provider<HybridDecrypt>, KeyReader() {
  @Inject lateinit var keyDecryptManager: HybridDecryptKeyManager
  @Inject lateinit var keyEncryptManager: HybridEncryptKeyManager

  override fun get(): HybridDecrypt {
    val keysetHandle = readKey(key)
    keyEncryptManager[key] =
        HybridEncryptFactory.getPrimitive(keysetHandle.publicKeysetHandle)
    return HybridDecryptFactory.getPrimitive(keysetHandle)
        .also { keyDecryptManager[key] = it }
  }
}

internal class StreamingAeadProvider(
  val key: KeyAlias,
) : Provider<StreamingAead>, KeyReader() {
  @Inject lateinit var streamingAeadKeyManager: StreamingAeadKeyManager

  override fun get(): StreamingAead {
    val keysetHandle = readKey(key)
    return StreamingAeadFactory.getPrimitive(keysetHandle)
        .also { streamingAeadKeyManager[key] = it }
  }
}
