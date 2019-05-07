package misk.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.KmsClient
import com.google.crypto.tink.Mac
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.PublicKeyVerify
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadFactory
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.aead.KmsEnvelopeAead
import com.google.crypto.tink.mac.MacConfig
import com.google.crypto.tink.mac.MacFactory
import com.google.crypto.tink.proto.KeyTemplate
import com.google.crypto.tink.signature.PublicKeySignFactory
import com.google.crypto.tink.signature.PublicKeyVerifyFactory
import com.google.crypto.tink.signature.SignatureConfig
import com.google.inject.Inject
import com.google.inject.Provider
import com.google.inject.Singleton
import com.google.inject.name.Names
import misk.config.Secret
import misk.inject.KAbstractModule
import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * Configures and registers the keys listed in the configuration file.
 * Each key is read, decrypted, and then bound via Google Guice and added to the [KeyManager].
 */
class CryptoModule(
  private val config: CryptoConfig
) : KAbstractModule() {

  override fun configure() {
    // no keys? no worries! exit early
    config.keys?: return
    config.kms_uri ?: throw IllegalArgumentException("Configuration value 'kms_uri' is missing")
    requireBinding(KmsClient::class.java)
    AeadConfig.register()
    MacConfig.register()
    SignatureConfig.register()

    val keyNames = config.keys.map { it.key_name }
    val duplicateNames = keyNames - config.keys.map { it.key_name }.distinct().toList()
    check(duplicateNames.isEmpty()) {
      "Found duplicate keys: [$duplicateNames]"
    }
    config.keys.forEach { key ->
      key.encrypted_key ?: throw IllegalArgumentException(
          "Configuration value 'encryped_key' is missing for key '${key.key_name}'")
      when(key.key_type) {
        KeyType.AEAD -> {
          bind<Aead>()
              .annotatedWith(Names.named(key.key_name))
              .toProvider(AeadEnvelopeProvider(config.kms_uri, key))
              .`in`(Singleton::class.java)
        }
        KeyType.MAC -> {
          bind<Mac>()
              .annotatedWith(Names.named(key.key_name))
              .toProvider(MacProvider(config.kms_uri, key))
              .`in`(Singleton::class.java)
        }
        KeyType.DIGITAL_SIGNATURE -> {
          bind<PublicKeySign>()
              .annotatedWith(Names.named(key.key_name))
              .toProvider(DigitalSignatureSignerProvider(config.kms_uri, key))
              .`in`(Singleton::class.java)
          bind<PublicKeyVerify>()
              .annotatedWith(Names.named(key.key_name))
              .toProvider(DigitalSignatureVerifierProvider(config.kms_uri, key))
              .`in`(Singleton::class.java)
        }
      }
    }
  }

  /**
   * We only support AEAD keys via envelope encryption.
   */
  private class AeadEnvelopeProvider(val keyUri: String, val key: Key) : Provider<Aead> {
    @Inject lateinit var keyManager: AeadKeyManager
    @Inject lateinit var kmsClient: KmsClient

    override fun get(): Aead {
      val keysetHandle = readKey(key.encrypted_key!!, kmsClient.getAead(keyUri))
      val kek = AeadFactory.getPrimitive(keysetHandle)
      val envelopeKey = KmsEnvelopeAead(DEK_TEMPLATE, kek)

      return envelopeKey.also { keyManager[key.key_name] = it }
    }

    companion object {
      val DEK_TEMPLATE : KeyTemplate = AeadKeyTemplates.AES128_GCM
    }
  }

  private class MacProvider(val keyUri: String, val key: Key) : Provider<Mac> {
    @Inject lateinit var keyManager: MacKeyManager
    @Inject lateinit var kmsClient: KmsClient

    override fun get(): Mac {
      val keysetHandle = readKey(key.encrypted_key!!, kmsClient.getAead(keyUri))
      return MacFactory.getPrimitive(keysetHandle)
          .also { keyManager[key.key_name] = it }
    }
  }

  private class DigitalSignatureSignerProvider(val keyUri: String, val key: Key
  ) : Provider<PublicKeySign> {
    @Inject lateinit var keyManager: DigitalSignatureKeyManager
    @Inject lateinit var kmsClient: KmsClient

    override fun get(): PublicKeySign {
      val keysetHandle = readKey(key.encrypted_key!!, kmsClient.getAead(keyUri))
      val signer = PublicKeySignFactory.getPrimitive(keysetHandle)
      val verifier = PublicKeyVerifyFactory.getPrimitive(keysetHandle.publicKeysetHandle)
      keyManager[key.key_name] = DigitalSignature(signer, verifier)
      return signer
    }
  }

  private class DigitalSignatureVerifierProvider(val keyUri: String, val key: Key
  ) : Provider<PublicKeyVerify> {
    @Inject lateinit var keyManager: DigitalSignatureKeyManager
    @Inject lateinit var kmsClient: KmsClient

    override fun get(): PublicKeyVerify {
      val keysetHandle = readKey(key.encrypted_key!!, kmsClient.getAead(keyUri))
      val signer = PublicKeySignFactory.getPrimitive(keysetHandle)
      val verifier = PublicKeyVerifyFactory.getPrimitive(keysetHandle.publicKeysetHandle)
      keyManager[key.key_name] = DigitalSignature(signer, verifier)
      return verifier
    }
  }

  companion object {
    internal fun readKey(keyConfig: Secret<String>, masterKey: Aead): KeysetHandle {
      return KeysetHandle.read(JsonKeysetReader.withString(keyConfig.value), masterKey)
    }
  }
}

/**
 * Extension function for convenient encryption of [ByteString]s.
 * This function also makes sure that no extra copies of the plaintext data are kept in memory.
 */
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
fun Aead.decrypt(ciphertext: ByteString, aad: ByteArray? = null): ByteString {
  val decryptedBytes = this.decrypt(ciphertext.toByteArray(), aad)
  val decrypted = decryptedBytes.toByteString()
  decryptedBytes.fill(0)
  return decrypted
}