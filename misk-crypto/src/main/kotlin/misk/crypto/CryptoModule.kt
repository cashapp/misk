package misk.crypto

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
import com.google.inject.name.Names
import misk.crypto.CiphertextFormat.InvalidCiphertextFormatException
import misk.inject.KAbstractModule
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.lang.IllegalArgumentException
import java.security.GeneralSecurityException
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
    HybridConfig.register()
    StreamingAeadConfig.register()

    val keyNames = config.keys.map { it.key_name }
    val duplicateNames = keyNames - keyNames.distinct().toList()
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
        KeyType.HYBRID_ENCRYPT -> {
          bind<HybridEncrypt>()
              .annotatedWith(Names.named(key.key_name))
              .toProvider(HybridEncryptProvider(key, config.kms_uri))
              .`in`(Singleton::class.java)
        }
        KeyType.HYBRID_ENCRYPT_DECRYPT -> {
          bind<HybridDecrypt>()
              .annotatedWith(Names.named(key.key_name))
              .toProvider(HybridDecryptProvider(key, config.kms_uri))
              .`in`(Singleton::class.java)
          bind<HybridEncrypt>()
              .annotatedWith(Names.named(key.key_name))
              .toProvider(HybridEncryptProvider(key, config.kms_uri))
              .`in`(Singleton::class.java)
        }
        KeyType.STREAMING_AEAD -> {
          bind<StreamingAead>()
              .annotatedWith(Names.named(key.key_name))
              .toProvider(StreamingAeadProvider(key, config.kms_uri))
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

fun Aead.encrypt(plaintext : ByteString, encryptionContext : Map<String, String>?) : ByteString {
  val plaintextBytes = plaintext.toByteArray()
  val aad = CiphertextFormat.serializeEncryptionContext(encryptionContext)
  val encrypted = this.encrypt(plaintextBytes, aad)
  plaintextBytes.fill(0)
  return CiphertextFormat.serialize(encrypted, aad).toByteString()
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

fun Aead.decrypt(ciphertext : ByteString, encryptionContext: Map<String, String>?) : ByteString {
  val (payload, aad) = try {
    CiphertextFormat.deserialize(ciphertext.toByteArray(), encryptionContext)
  } catch (e: InvalidCiphertextFormatException) {
    Pair(ciphertext.toByteArray(), CiphertextFormat.serializeEncryptionContext(encryptionContext))
  }
  val decryptedBytes = this.decrypt(payload, aad)
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
) : ByteString {
  val plaintextBytes = plaintext.toByteArray()
  val encrypted = this.encryptDeterministically(plaintextBytes, aad ?: byteArrayOf())
  plaintextBytes.fill(0)
  return encrypted.toByteString()
}

fun DeterministicAead.encryptDeterministically(
  plaintext: ByteString,
  encryptionContext: Map<String, String>?
): ByteString {
  val plaintextBytes = plaintext.toByteArray()
  val aad = CiphertextFormat.serializeEncryptionContext(encryptionContext)
  val encrypted = this.encryptDeterministically(plaintextBytes, aad ?: byteArrayOf())
  plaintextBytes.fill(0)
  return CiphertextFormat.serialize(encrypted, aad).toByteString()
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
) : ByteString {
  val decryptedBytes = this.decryptDeterministically(ciphertext.toByteArray(), aad)
  val decrypted = decryptedBytes.toByteString()
  decryptedBytes.fill(0)
  return decrypted
}

fun DeterministicAead.decryptDeterministically(
  ciphertext: ByteString,
  encryptionContext: Map<String, String>?
): ByteString {
  val bytes = ciphertext.toByteArray()
  val (payload, aad) = try {
    CiphertextFormat.deserialize(bytes, encryptionContext)
  } catch(e: InvalidCiphertextFormatException) {
    Pair(bytes, CiphertextFormat.serializeEncryptionContext(encryptionContext))
  }
  val decryptedBytes = this.decryptDeterministically(payload, aad ?: byteArrayOf())
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

fun HybridEncrypt.encrypt(
  plaintext: ByteString,
  encryptionContext: Map<String, String>?
): ByteString {
  val plaintextBytes = plaintext.toByteArray()
  val aad = CiphertextFormat.serializeEncryptionContext(encryptionContext)
  val ciphertext = this.encrypt(plaintextBytes, aad)
  plaintextBytes.fill(0)
  return CiphertextFormat.serialize(ciphertext, aad).toByteString()
}

fun HybridDecrypt.decrypt(
  ciphertext: ByteString,
  encryptionContext: Map<String, String>?
): ByteString {
  val (ciphertextBytes, aad) = try {
    CiphertextFormat.deserialize(ciphertext.toByteArray(), encryptionContext)
  } catch (e: InvalidCiphertextFormatException) {
    Pair(ciphertext.toByteArray(), CiphertextFormat.serializeEncryptionContext(encryptionContext))
  }
  val plaintextBytes = this.decrypt(ciphertextBytes, aad)
  val plaintext = plaintextBytes.toByteString()
  plaintextBytes.fill(0)
  return plaintext
}