package misk.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.Mac
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.PublicKeyVerify
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadFactory
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.mac.MacConfig
import com.google.crypto.tink.aead.KmsEnvelopeAead
import com.google.crypto.tink.mac.MacFactory
import com.google.crypto.tink.mac.MacKeyTemplates
import com.google.crypto.tink.signature.PublicKeySignFactory
import com.google.crypto.tink.signature.PublicKeyVerifyFactory
import com.google.crypto.tink.signature.SignatureConfig
import com.google.crypto.tink.signature.SignatureKeyTemplates
import com.google.inject.name.Names
import misk.inject.KAbstractModule
import javax.inject.Inject
import javax.inject.Provider

/**
 * This module should be used for testing purposes only.
 * It generates random keys for each key name specified in the configuration
 * and uses [FakeKmsClient] instead of a real KMS service.
 *
 * This module **will** read the exact same configuration files as the real application,
 * but **will not** use the key material specified in the configuration.
 * Instead, it'll generate a random keyset handle for each named key.
 */
class CryptoTestModule(
  private val keyNames: List<Key>
) : KAbstractModule() {

  override fun configure() {
    AeadConfig.register()
    MacConfig.register()
    SignatureConfig.register()

    keyNames.forEach { key ->
      when(key.key_type) {
        KeyType.AEAD -> {
          bind<Aead>()
              .annotatedWith(Names.named(key.key_name))
              .toProvider(CipherProvider(key.key_name))
              .asEagerSingleton()
        }
        KeyType.MAC -> {
          bind<Mac>()
              .annotatedWith(Names.named(key.key_name))
              .toProvider(MacProvider(key.key_name))
              .asEagerSingleton()
        }
        KeyType.DIGITAL_SIGNATURE -> {
          val keysetHandle = KeysetHandle.generateNew(SignatureKeyTemplates.ED25519)
          val signer = PublicKeySignFactory.getPrimitive(keysetHandle)
          val verifier = PublicKeyVerifyFactory.getPrimitive(keysetHandle.publicKeysetHandle)
          bind<PublicKeySign>()
              .annotatedWith(Names.named(key.key_name))
              .toProvider(DigitalSignatureSignerProvider(signer, verifier, key.key_name))
              .asEagerSingleton()
          bind<PublicKeyVerify>()
              .annotatedWith(Names.named(key.key_name))
              .toInstance(verifier)
        }
      }
    }
  }

  private class CipherProvider(val keyName: String) : Provider<Aead> {
    @Inject lateinit var keyManager: AeadKeyManager

    override fun get(): Aead {
      val keysetHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
      val kek = AeadFactory.getPrimitive(keysetHandle)
      val envelopeKey = KmsEnvelopeAead(AeadKeyTemplates.AES128_GCM, kek)
      return envelopeKey.also { keyManager[keyName] = it }
    }
  }

  private class MacProvider(val keyName: String) : Provider<Mac> {
    @Inject lateinit var keyManager: MacKeyManager

    override fun get(): Mac {
      val keysetHandle = KeysetHandle.generateNew(MacKeyTemplates.HMAC_SHA256_256BITTAG)
      return MacFactory.getPrimitive(keysetHandle)
          .also { keyManager[keyName] = it }
    }
  }

  private class DigitalSignatureSignerProvider(val signer: PublicKeySign,
    val verifier: PublicKeyVerify, val name: String) : Provider<PublicKeySign> {
    @Inject lateinit var keyManager: DigitalSignatureKeyManager

    override fun get(): PublicKeySign {
      keyManager[name] = DigitalSignature(signer, verifier)
      return signer
    }
  }
}
