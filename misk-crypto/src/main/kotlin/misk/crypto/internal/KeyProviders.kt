package misk.crypto.internal

import com.google.crypto.tink.Aead
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.Mac
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.PublicKeyVerify
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.daead.DeterministicAeadFactory
import com.google.crypto.tink.hybrid.HybridDecryptFactory
import com.google.crypto.tink.hybrid.HybridEncryptFactory
import com.google.crypto.tink.mac.MacFactory
import com.google.crypto.tink.signature.PublicKeySignFactory
import com.google.crypto.tink.signature.PublicKeyVerifyFactory
import com.google.crypto.tink.streamingaead.StreamingAeadFactory
import com.google.inject.Inject
import com.google.inject.Provider
import misk.crypto.AeadKeyManager
import misk.crypto.DeterministicAeadKeyManager
import misk.crypto.DigitalSignature
import misk.crypto.DigitalSignatureKeyManager
import misk.crypto.HybridDecryptKeyManager
import misk.crypto.HybridEncryptKeyManager
import misk.crypto.KeyAlias
import misk.crypto.KeyReader
import misk.crypto.MacKeyManager
import misk.crypto.StreamingAeadKeyManager
import java.security.GeneralSecurityException

/**
 * We only support AEAD keys via envelope encryption.
 */
class AeadEnvelopeProvider(
  val key: KeyAlias,
) : Provider<Aead>, KeyReader() {
  @Inject lateinit var keyManager: AeadKeyManager

  override fun get(): Aead {
    val keysetHandle = readKey(key)
    val aeadKey = keysetHandle.getPrimitive(Aead::class.java)

    return aeadKey.also { keyManager[key] = it }
  }
}

class DeterministicAeadProvider(
  val key: KeyAlias,
) : Provider<DeterministicAead>, KeyReader() {
  @Inject lateinit var keyManager: DeterministicAeadKeyManager

  override fun get(): DeterministicAead {
    val keysetHandle = readKey(key)
    val daeadKey = DeterministicAeadFactory.getPrimitive(keysetHandle)

    return daeadKey.also { keyManager[key] = it }
  }
}

class MacProvider(
  val key: KeyAlias,
) : Provider<Mac>, KeyReader() {
  @Inject lateinit var keyManager: MacKeyManager

  override fun get(): Mac {
    val keysetHandle = readKey(key)
    return MacFactory.getPrimitive(keysetHandle)
      .also { keyManager[key] = it }
  }
}

class DigitalSignatureSignerProvider(
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

class DigitalSignatureVerifierProvider(
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

class HybridEncryptProvider(
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
      .also { keyManager[key] = it }
  }
}

class HybridDecryptProvider(
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

class StreamingAeadProvider(
  val key: KeyAlias,
) : Provider<StreamingAead>, KeyReader() {
  @Inject lateinit var streamingAeadKeyManager: StreamingAeadKeyManager

  override fun get(): StreamingAead {
    val keysetHandle = readKey(key)
    return StreamingAeadFactory.getPrimitive(keysetHandle)
      .also { streamingAeadKeyManager[key] = it }
  }
}
