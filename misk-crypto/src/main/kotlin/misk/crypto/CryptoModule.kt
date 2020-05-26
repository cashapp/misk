package misk.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.KmsClient
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.mac.MacConfig
import com.google.crypto.tink.signature.SignatureConfig
import misk.inject.KAbstractModule
import com.google.crypto.tink.Mac
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.PublicKeyVerify
import com.google.crypto.tink.daead.DeterministicAeadConfig
import com.google.inject.Singleton
import com.google.inject.name.Names
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.Base64

/**
 * Configures and registers the keys listed in the configuration file.
 * Each key is read, decrypted, and then bound via Google Guice and added to the [KeyManager].
 */
class CryptoModule(
  private val config: CryptoConfig
) : KAbstractModule() {

  override fun configure() {
    // no keys? no worries! exit early
    config.keys ?: return
    requireBinding(KmsClient::class.java)
    AeadConfig.register()
    DeterministicAeadConfig.register()
    MacConfig.register()
    SignatureConfig.register()

    val keyNames = config.keys.map { it.key_name }
    val duplicateNames = keyNames - config.keys.map { it.key_name }.distinct().toList()
    check(duplicateNames.isEmpty()) {
      "Found duplicate keys: [$duplicateNames]"
    }

    config.keys.forEach { key ->
      when (key.key_type) {
        KeyType.AEAD -> {
          bind<Aead>()
              .annotatedWith(Names.named(key.key_name))
              .toProvider(AeadEnvelopeProvider(key, config.kms_uri))
              .`in`(Singleton::class.java)
        }
        KeyType.DAEAD -> {
          bind<DeterministicAead>()
              .annotatedWith(Names.named(key.key_name))
              .toProvider(DeterministicAeadProvider(key, config.kms_uri))
              .`in`(Singleton::class.java)
        }
        KeyType.MAC -> {
          bind<Mac>()
              .annotatedWith(Names.named(key.key_name))
              .toProvider(MacProvider(key, config.kms_uri))
              .`in`(Singleton::class.java)
        }
        KeyType.DIGITAL_SIGNATURE -> {
          bind<PublicKeySign>()
              .annotatedWith(Names.named(key.key_name))
              .toProvider(DigitalSignatureSignerProvider(key, config.kms_uri))
              .`in`(Singleton::class.java)
          bind<PublicKeyVerify>()
              .annotatedWith(Names.named(key.key_name))
              .toProvider(DigitalSignatureVerifierProvider(key, config.kms_uri))
              .`in`(Singleton::class.java)
        }
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
  val decodedTag = Base64.getDecoder().decode(tag)
  this.verifyMac(decodedTag, data.toByteArray())
}