package misk.crypto

import com.amazonaws.services.s3.AmazonS3
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
import com.google.inject.Singleton
import com.google.inject.TypeLiteral
import com.google.inject.name.Names
import misk.crypto.pgp.PgpDecrypter
import misk.crypto.pgp.PgpDecrypterProvider
import misk.crypto.pgp.PgpEncrypter
import misk.crypto.pgp.PgpEncrypterProvider
import misk.inject.KAbstractModule
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.GeneralSecurityException
import java.security.Security
import java.util.Base64

/**
 * Configures and registers the keys listed in the configuration file.
 * Each key is read, decrypted, and then bound via Google Guice and added to a [MappedKeyManager].
 */
class CryptoModule(
  private val config: CryptoConfig
) : KAbstractModule() {

  override fun configure() {
    requireBinding<KmsClient>()

    AeadConfig.register()
    DeterministicAeadConfig.register()
    MacConfig.register()
    SignatureConfig.register()
    HybridConfig.register()
    StreamingAeadConfig.register()
    Security.addProvider(BouncyCastleProvider())

    var keyNames = listOf<KeyAlias>()

    /* All the key providers in this multibinder share a namespace. For example, a key with a given
     * name can only exist in one of the providers. This makes migrating keys between stores less
     * error-prone.
     */
    val keyManagerBinder = newMultibinder(KeyResolver::class)
    val serviceKeys = mutableMapOf<KeyAlias, KeyType>()

    /* Parse and include all local keys first. */
    config.keys?.let { keys ->
      try {
        keys.map { it.encrypted_key }.requireNoNulls()
      } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Found local key with no 'encrypted_key' value", e)
      }
      keyManagerBinder.addBinding().toInstance(LocalConfigKeyResolver(keys, config.kms_uri))

      keys.forEach {
        bindKeyToProvider(it.key_name, it.key_type)
        serviceKeys[it.key_name] = it.key_type
      }

      keyNames = keys.map { it.key_name }
      val duplicateNames = keyNames - keyNames.distinct().toList()
      check(duplicateNames.isEmpty()) {
        "Found duplicate keys: [$duplicateNames]"
      }
    }

    bind(object : TypeLiteral<Map<KeyAlias, KeyType>>() {})
      .annotatedWith(ServiceKeys::class.java)
      .toInstance(serviceKeys.toMap())

    val externalDataKeys = config.external_data_keys ?: emptyMap()
    bind(object : TypeLiteral<Map<KeyAlias, KeyType>>() {})
      .annotatedWith(ExternalDataKeys::class.java)
      .toInstance(externalDataKeys)

    /* Include all configured remotely-provided keys. */
    if (externalDataKeys.isNotEmpty()) {
      requireBinding<AmazonS3>()

      newMultibinder<ExternalKeySource>()
      multibind<ExternalKeySource>().to<S3KeySource>()

      keyManagerBinder.addBinding().to<ExternalKeyResolver>().`in`(Singleton::class.java)

      val internalAndExternal = keyNames.intersect(externalDataKeys.keys)
      check(internalAndExternal.isEmpty()) {
        "Found keys that are marked as both provided in resources, and provided externally: " +
          "[$internalAndExternal]"
      }

      externalDataKeys.forEach { (alias, type) ->
        // External keys use a KMS key per keyset
        bindKeyToProvider(alias, type)
      }
    }
  }

  private fun bindKeyToProvider(alias: KeyAlias, type: KeyType) {
    when (type) {
      KeyType.AEAD -> {
        bind<Aead>()
          .annotatedWith(Names.named(alias))
          .toProvider(AeadEnvelopeProvider(alias))
          .`in`(Singleton::class.java)
      }
      KeyType.DAEAD -> {
        bind<DeterministicAead>()
          .annotatedWith(Names.named(alias))
          .toProvider(DeterministicAeadProvider(alias))
          .`in`(Singleton::class.java)
      }
      KeyType.MAC -> {
        bind<Mac>()
          .annotatedWith(Names.named(alias))
          .toProvider(MacProvider(alias))
          .`in`(Singleton::class.java)
      }
      KeyType.DIGITAL_SIGNATURE -> {
        bind<PublicKeySign>()
          .annotatedWith(Names.named(alias))
          .toProvider(DigitalSignatureSignerProvider(alias))
          .`in`(Singleton::class.java)
        bind<PublicKeyVerify>()
          .annotatedWith(Names.named(alias))
          .toProvider(DigitalSignatureVerifierProvider(alias))
          .`in`(Singleton::class.java)
      }
      KeyType.HYBRID_ENCRYPT -> {
        bind<HybridEncrypt>()
          .annotatedWith(Names.named(alias))
          .toProvider(HybridEncryptProvider(alias))
          .`in`(Singleton::class.java)
      }
      KeyType.HYBRID_ENCRYPT_DECRYPT -> {
        bind<HybridDecrypt>()
          .annotatedWith(Names.named(alias))
          .toProvider(HybridDecryptProvider(alias))
          .`in`(Singleton::class.java)
        bind<HybridEncrypt>()
          .annotatedWith(Names.named(alias))
          .toProvider(HybridEncryptProvider(alias))
          .`in`(Singleton::class.java)
      }
      KeyType.STREAMING_AEAD -> {
        bind<StreamingAead>()
          .annotatedWith(Names.named(alias))
          .toProvider(StreamingAeadProvider(alias))
          .`in`(Singleton::class.java)
      }
      KeyType.PGP_DECRYPT -> {
        bind<PgpDecrypter>()
          .annotatedWith(Names.named(alias))
          .toProvider(PgpDecrypterProvider(alias))
          .`in`(Singleton::class.java)
      }
      KeyType.PGP_ENCRYPT -> {
        bind<PgpEncrypter>()
          .annotatedWith(Names.named(alias))
          .toProvider(PgpEncrypterProvider(alias))
          .`in`(Singleton::class.java)
      }
    }
  }
}

/**
 * Extension function for convenient encryption of [ByteString]s.
 * This function also makes sure that no extra copies of the plaintext data are kept in memory.
 */
@Deprecated(
  message = "This method is marked for deletion, for now use the raw interface provided by Tink",
  replaceWith = ReplaceWith(
    expression = "aead.encrypt(ByteArray, ByteArray)"
  ),
  level = DeprecationLevel.HIDDEN
)
fun Aead.encrypt(plaintext: ByteString, aad: ByteArray? = null): ByteString {
  val plaintextBytes = plaintext.toByteArray()
  val encrypted = this.encrypt(plaintextBytes, aad)
  plaintextBytes.fill(0)
  return encrypted.toByteString()
}

/**
 * Extension function for convenient decryption of [ByteString]s.
 * This function also makes sure that no extra copies of the plaintext data are kept in memory.
 */
@Deprecated(
  message = "This method is marked for deletion, for now use the raw interface provided by Tink",
  replaceWith = ReplaceWith(
    expression = "aead.decrypt(ByteArray, ByteArray)"
  ),
  level = DeprecationLevel.HIDDEN
)
fun Aead.decrypt(ciphertext: ByteString, aad: ByteArray? = null): ByteString {
  val decryptedBytes = this.decrypt(ciphertext.toByteArray(), aad)
  val decrypted = decryptedBytes.toByteString()
  decryptedBytes.fill(0)
  return decrypted
}

/**
 * Extension function for convenient encryption of [ByteString]s.
 * This function also makes sure that no extra copies of the plaintext data are kept in memory.
 */
@Deprecated(
  message = "This method is marked for deletion, for now use the raw interface provided by Tink",
  replaceWith = ReplaceWith(
    expression = "daead.encryptDeterministically(ByteArray, ByteArray)"
  ),
  level = DeprecationLevel.HIDDEN
)
fun DeterministicAead.encryptDeterministically(
  plaintext: ByteString,
  aad: ByteArray? = null
): ByteString {
  val plaintextBytes = plaintext.toByteArray()
  val encrypted = this.encryptDeterministically(plaintextBytes, aad ?: byteArrayOf())
  plaintextBytes.fill(0)
  return encrypted.toByteString()
}

/**
 * Extension function for convenient decryption of [ByteString]s.
 * This function also makes sure that no extra copies of the plaintext data are kept in memory.
 */
@Deprecated(
  message = "This method is marked for deletion, for now use the raw interface provided by Tink",
  replaceWith = ReplaceWith(
    expression = "daead.decryptDeterministically(ByteArray, ByteArray)"
  ),
  level = DeprecationLevel.HIDDEN
)
fun DeterministicAead.decryptDeterministically(
  ciphertext: ByteString,
  aad: ByteArray? = null
): ByteString {
  val decryptedBytes = this.decryptDeterministically(ciphertext.toByteArray(), aad)
  val decrypted = decryptedBytes.toByteString()
  decryptedBytes.fill(0)
  return decrypted
}

/**
 * Extension function for conveniently computing an HMAC and encoding it with Base64.
 */
fun Mac.computeMac(data: String): String {
  return Base64.getEncoder().encode(this.computeMac(data.toByteArray())).toString(Charsets.UTF_8)
}

/**
 * Extension function for conveniently verifying a message's authenticity.
 * This function expects the [tag] string variable to contain a [Base64] encoded array of bytes.
 */
fun Mac.verifyMac(tag: String, data: String) {
  val decodedTag = try {
    Base64.getDecoder().decode(tag)
  } catch (e: IllegalArgumentException) {
    throw GeneralSecurityException(String.format("invalid tag: %s", tag), e)
  }
  this.verifyMac(decodedTag, data.toByteArray())
}
