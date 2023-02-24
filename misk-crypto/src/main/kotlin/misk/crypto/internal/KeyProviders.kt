package misk.crypto.internal

import com.google.crypto.tink.Aead
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.Mac
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.PublicKeyVerify
import com.google.crypto.tink.StreamingAead
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
    return misk.crypto.internal.Aead(
      getRawKey(key), keysetHandle.getPrimitive(Aead::class.java), metrics
    ).also {
      keyManager[key] = it
    }
  }
}

class DeterministicAeadProvider(
  val key: KeyAlias,
) : Provider<DeterministicAead>, KeyReader() {
  @Inject lateinit var keyManager: DeterministicAeadKeyManager

  override fun get(): DeterministicAead {
    val keysetHandle = readKey(key)
    return misk.crypto.internal.DeterministicAead(
      getRawKey(key), keysetHandle.getPrimitive(DeterministicAead::class.java), metrics
    ).also {
      keyManager[key] = it
    }
  }
}

class MacProvider(
  val key: KeyAlias,
) : Provider<Mac>, KeyReader() {
  @Inject lateinit var keyManager: MacKeyManager

  override fun get(): Mac {
    val keysetHandle = readKey(key)
    return misk.crypto.internal.Mac(
      getRawKey(key),
      keysetHandle.getPrimitive(Mac::class.java),
      metrics
    )
      .also { keyManager[key] = it }
  }
}

class DigitalSignatureSignerProvider(
  val key: KeyAlias,
) : Provider<PublicKeySign>, KeyReader() {
  @Inject lateinit var keyManager: DigitalSignatureKeyManager

  override fun get(): PublicKeySign {
    val keysetHandle = readKey(key)
    val rawKey = getRawKey(key)
    val signer = misk.crypto.internal.PublicKeySign(
      rawKey,
      keysetHandle.getPrimitive(PublicKeySign::class.java),
      metrics
    )
    val verifier = misk.crypto.internal.PublicKeyVerify(
      rawKey,
      keysetHandle.publicKeysetHandle.getPrimitive(PublicKeyVerify::class.java),
      metrics
    )
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
    val rawKey = getRawKey(key)
    val signer = misk.crypto.internal.PublicKeySign(
      rawKey,
      keysetHandle.getPrimitive(PublicKeySign::class.java),
      metrics
    )
    val verifier = misk.crypto.internal.PublicKeyVerify(
      rawKey,
      keysetHandle.publicKeysetHandle.getPrimitive(PublicKeyVerify::class.java),
      metrics
    )
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
    return misk.crypto.internal.HybridEncrypt(
      getRawKey(key),
      publicKeysetHandle.getPrimitive(HybridEncrypt::class.java),
      metrics
    )
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
    val rawKey = getRawKey(key)
    keyEncryptManager[key] =
      misk.crypto.internal.HybridEncrypt(
        rawKey,
        keysetHandle.publicKeysetHandle.getPrimitive(HybridEncrypt::class.java),
        metrics
      )
    return misk.crypto.internal.HybridDecrypt(
      rawKey,
      keysetHandle.getPrimitive(HybridDecrypt::class.java),
      metrics
    )
      .also { keyDecryptManager[key] = it }
  }
}

class StreamingAeadProvider(
  val key: KeyAlias,
) : Provider<StreamingAead>, KeyReader() {
  @Inject lateinit var streamingAeadKeyManager: StreamingAeadKeyManager

  override fun get(): StreamingAead {
    val keysetHandle = readKey(key)
    return misk.crypto.internal.StreamingAead(
      getRawKey(key), keysetHandle.getPrimitive(StreamingAead::class.java), metrics
    )
      .also { streamingAeadKeyManager[key] = it }
  }
}
