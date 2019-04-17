package misk.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.KmsClient
import com.google.crypto.tink.Mac
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadFactory
import com.google.crypto.tink.mac.MacFactory
import com.google.inject.Inject
import com.google.inject.Provider
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
    requireBinding(KmsClient::class.java)
    AeadConfig.register()

    check(config.keys?.map { it.key_name }?.distinct()?.size == config.keys?.size) {
      "Found duplicate key name"
    }
    config.keys?.forEach { key ->
      when(key.key_type) {
        KeyType.ENCRYPTION -> {
          bind<Aead>()
              .annotatedWith(Names.named(key.key_name))
              .toProvider(CipherProvider(config.kms_uri, key))
        }
        KeyType.MAC -> {
          bind<Mac>()
              .annotatedWith(Names.named(key.key_name))
              .toProvider(MacProvider(config.kms_uri, key))
        }
      }
    }
  }

  private class CipherProvider(val keyUri: String, val key: Key) : Provider<Aead> {
    @Inject lateinit var keyManager: AeadKeyManager
    @Inject lateinit var kmsClient: KmsClient

    override fun get(): Aead {
      val keysetHandle = readKey(key.encrypted_key, kmsClient.getAead(keyUri))
      return AeadFactory.getPrimitive(keysetHandle)
          .also { keyManager[key.key_name] = it }
    }

  }

  private class MacProvider(val keyUri: String, val key: Key) : Provider<Mac> {
    @Inject lateinit var keyManager: MacKeyManager
    @Inject lateinit var kmsClient: KmsClient

    override fun get(): Mac {
      val keysetHandle = readKey(key.encrypted_key, kmsClient.getAead(keyUri))
      return MacFactory.getPrimitive(keysetHandle)
          .also { keyManager[key.key_name] = it }
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
fun Aead.encrypt(plaintext: ByteString): ByteString {
  val plaintextBytes = plaintext.toByteArray()
  val encrypted = this.encrypt(plaintextBytes, null)
  plaintextBytes.fill(0)
  return encrypted.toByteString()
}

/**
 * Extension function for convenient decryption of [ByteString]s.
 * This function also makes sure that no extra copies of the plaintext data are kept in memory.
 */
fun Aead.decrypt(ciphertext: ByteString): ByteString {
  val decryptedBytes = this.decrypt(ciphertext.toByteArray(), null)
  val decrypted = decryptedBytes.toByteString()
  decryptedBytes.fill(0)
  return decrypted
}