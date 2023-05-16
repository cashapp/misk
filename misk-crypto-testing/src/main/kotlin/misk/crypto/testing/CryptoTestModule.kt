package misk.crypto.testing

import com.google.crypto.tink.Aead
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KmsClient
import com.google.crypto.tink.Mac
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.PublicKeyVerify
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.daead.DeterministicAeadConfig
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.mac.MacConfig
import com.google.crypto.tink.signature.SignatureConfig
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import com.google.inject.TypeLiteral
import com.google.inject.name.Names
import misk.config.MiskConfig
import misk.crypto.CryptoConfig
import misk.crypto.ExternalDataKeys
import misk.crypto.Key
import misk.crypto.KeyAlias
import misk.crypto.KeyResolver
import misk.crypto.KeyType
import misk.crypto.ServiceKeys
import misk.crypto.internal.AeadEnvelopeProvider
import misk.crypto.internal.DeterministicAeadProvider
import misk.crypto.internal.DigitalSignatureSignerProvider
import misk.crypto.internal.DigitalSignatureVerifierProvider
import misk.crypto.internal.HybridDecryptProvider
import misk.crypto.internal.HybridEncryptProvider
import misk.crypto.internal.MacProvider
import misk.crypto.internal.StreamingAeadProvider
import misk.crypto.pgp.PgpDecrypter
import misk.crypto.pgp.PgpEncrypter
import misk.crypto.pgp.internal.PgpDecrypterProvider
import misk.crypto.pgp.internal.PgpEncrypterProvider
import misk.inject.KAbstractModule
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * This module should be used for testing purposes only.
 * It generates random keys for each key name specified in the configuration
 * and uses [FakeKmsClient] instead of a real KMS service.
 *
 * This module **will** read the exact same configuration files as the real application,
 * but **will not** use the key material specified in the configuration.
 * Instead, it'll generate a random keyset handle for each named key.
 */
@Deprecated("Replace your dependency on misk-crypto-testing with `testImplementation(testFixtures(Dependencies.miskCrypto))`")
class CryptoTestModule(
  private val config: CryptoConfig? = null
) : KAbstractModule() {

  override fun configure() {

    bind<KmsClient>().toInstance(FakeKmsClient())

    AeadConfig.register()
    DeterministicAeadConfig.register()
    MacConfig.register()
    SignatureConfig.register()
    HybridConfig.register()
    StreamingAeadConfig.register()
    Security.addProvider(BouncyCastleProvider())

    config ?: return
    val keys = mutableListOf<Key>()
    config.keys?.let { keys.addAll(it) }

    val keyManagerBinder = newMultibinder(KeyResolver::class)
    keyManagerBinder.addBinding().toInstance(FakeKeyResolver(keys))

    val externalDataKeys = config.external_data_keys ?: emptyMap()
    bind(object : TypeLiteral<Map<KeyAlias, KeyType>>() {})
      .annotatedWith(ExternalDataKeys::class.java)
      .toInstance(externalDataKeys)
    keyManagerBinder.addBinding().toInstance(FakeKeyResolver(externalDataKeys))

    if (externalDataKeys.isNotEmpty()) {
      externalDataKeys.entries.forEach { entry ->
        val fakeFake = Key(entry.key, entry.value, MiskConfig.RealSecret(""))
        keys.add(fakeFake)
      }
    }

    val serviceKeys = keys.associate { it.key_name to it.key_type }
    bind(object : TypeLiteral<Map<KeyAlias, KeyType>>() {})
      .annotatedWith(ServiceKeys::class.java)
      .toInstance(serviceKeys.toMap())

    keys.forEach { key ->
      when (key.key_type) {
        KeyType.AEAD -> {
          bind<Aead>()
            .annotatedWith(Names.named(key.key_name))
            .toProvider(AeadEnvelopeProvider(key.key_name))
            .asEagerSingleton()
        }

        KeyType.DAEAD -> {
          bind<DeterministicAead>()
            .annotatedWith(Names.named(key.key_name))
            .toProvider(DeterministicAeadProvider(key.key_name))
            .asEagerSingleton()
        }

        KeyType.MAC -> {
          bind<Mac>()
            .annotatedWith(Names.named(key.key_name))
            .toProvider(MacProvider(key.key_name))
            .asEagerSingleton()
        }

        KeyType.DIGITAL_SIGNATURE -> {
          bind<PublicKeySign>()
            .annotatedWith(Names.named(key.key_name))
            .toProvider(DigitalSignatureSignerProvider(key.key_name))
            .asEagerSingleton()
          bind<PublicKeyVerify>()
            .annotatedWith(Names.named(key.key_name))
            .toProvider(DigitalSignatureVerifierProvider(key.key_name))
            .asEagerSingleton()
        }

        KeyType.HYBRID_ENCRYPT -> {
          bind<HybridEncrypt>()
            .annotatedWith(Names.named(key.key_name))
            .toProvider(HybridEncryptProvider(key.key_name))
            .asEagerSingleton()
        }

        KeyType.HYBRID_ENCRYPT_DECRYPT -> {
          bind<HybridDecrypt>()
            .annotatedWith(Names.named(key.key_name))
            .toProvider(HybridDecryptProvider(key.key_name))
            .asEagerSingleton()
          bind<HybridEncrypt>()
            .annotatedWith(Names.named(key.key_name))
            .toProvider(HybridEncryptProvider(key.key_name))
            .asEagerSingleton()
        }

        KeyType.STREAMING_AEAD -> {
          bind<StreamingAead>()
            .annotatedWith(Names.named(key.key_name))
            .toProvider(StreamingAeadProvider(key.key_name))
            .asEagerSingleton()
        }

        KeyType.PGP_DECRYPT -> {
          bind<PgpDecrypter>()
            .annotatedWith(Names.named(key.key_name))
            .toProvider(PgpDecrypterProvider(key.key_name))
            .asEagerSingleton()
        }

        KeyType.PGP_ENCRYPT -> {
          bind<PgpEncrypter>()
            .annotatedWith(Names.named(key.key_name))
            .toProvider(PgpEncrypterProvider(key.key_name))
            .asEagerSingleton()
        }
      }
    }
  }
}
