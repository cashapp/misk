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
import com.google.crypto.tink.KeysetManager
import com.google.crypto.tink.aead.AeadFactory
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.aead.KmsEnvelopeAead
import com.google.crypto.tink.daead.DeterministicAeadFactory
import com.google.crypto.tink.hybrid.HybridDecryptFactory
import com.google.crypto.tink.hybrid.HybridEncryptFactory
import com.google.inject.Inject
import com.google.inject.Provider
import misk.logging.getLogger
import java.security.GeneralSecurityException

open class KeyReader {
  companion object {
    val KEK_TEMPLATE: KeyTemplate = AeadKeyTemplates.AES256_GCM
  }

  val logger by lazy { getLogger<KeyReader>() }

  private fun readCleartextKey(key: Key): KeysetHandle {
    // TODO: Implement a clean check to throw if we are running in prod or staging. Checking for
    // an injected Environment will fail if a test explicitly creates a staging/prod environment.
    logger.warn { "reading a plaintext key!" }
    val reader = JsonKeysetReader.withString(key.encrypted_key.value)
    return CleartextKeysetHandle.read(reader)
  }

  private fun readEncryptedKey(key: Key, kmsUri: String, client: KmsClient): KeysetHandle {
    val masterKey = client.getAead(kmsUri)
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

  fun readKey(key: Key, kmsUri: String?, kmsClient: KmsClient): KeysetHandle {
    return if (kmsUri != null) {
      readEncryptedKey(key, kmsUri, kmsClient)
    } else {
      readCleartextKey(key)
    }
  }
}

/**
 * We only support AEAD keys via envelope encryption.
 */
internal class AeadEnvelopeProvider(
  val key: Key,
  private val kmsUri: String?
) : Provider<Aead>, KeyReader() {
  @Inject lateinit var keyManager: AeadKeyManager
  @Inject lateinit var kmsClient: KmsClient

  override fun get(): Aead {
    val keysetHandle = readKey(key, kmsUri, kmsClient)
    val aeadKey = AeadFactory.getPrimitive(keysetHandle)

    return aeadKey.also { keyManager[key.key_name] = it }
  }
}

internal class DeterministicAeadProvider(
  val key: Key,
  private val kmsUri: String?
) : Provider<DeterministicAead>, KeyReader() {
  @Inject lateinit var keyManager: DeterministicAeadKeyManager
  @Inject lateinit var kmsClient: KmsClient

  override fun get(): DeterministicAead {
    val keysetHandle = readKey(key, kmsUri, kmsClient)
    val daeadKey = DeterministicAeadFactory.getPrimitive(keysetHandle)

    return daeadKey.also { keyManager[key.key_name] = it }
  }
}

internal class MacProvider(
  val key: Key,
  private val kmsUri: String?
) : Provider<Mac>, KeyReader() {
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
  private val kmsUri: String?
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
  private val kmsUri: String?
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

internal class HybridEncryptProvider(
  val key: Key,
  private val kmsUri: String?
) : Provider<HybridEncrypt>, KeyReader() {
  @Inject lateinit var keyManager: HybridEncryptKeyManager
  @Inject lateinit var kmsClient: KmsClient

  override fun get(): HybridEncrypt {
    val keysetHandle = readKey(key, kmsUri, kmsClient)
    val publicKeysetHandle = try {
      keysetHandle.publicKeysetHandle
    } catch (e: GeneralSecurityException) {
      keysetHandle
    }
    return HybridEncryptFactory.getPrimitive(publicKeysetHandle)
        .also {keyManager[key.key_name] = it }
  }
}

internal class HybridDecryptProvider(
  val key: Key,
  private val kmsUri: String?
) : Provider<HybridDecrypt>, KeyReader() {
  @Inject lateinit var keyDecryptManager: HybridDecryptKeyManager
  @Inject lateinit var keyEncryptManager: HybridEncryptKeyManager
  @Inject lateinit var kmsClient: KmsClient

  override fun get(): HybridDecrypt {
    val keysetHandle = readKey(key, kmsUri, kmsClient)
    keyEncryptManager[key.key_name] =
        HybridEncryptFactory.getPrimitive(keysetHandle.publicKeysetHandle)
    return HybridDecryptFactory.getPrimitive(keysetHandle)
        .also { keyDecryptManager[key.key_name] = it }
  }
}
