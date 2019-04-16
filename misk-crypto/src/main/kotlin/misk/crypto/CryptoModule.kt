package misk.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.KmsClient
import com.google.crypto.tink.aead.AeadConfig
import com.google.inject.Inject
import com.google.inject.Provider
import com.google.inject.name.Names
import misk.config.Secret
import misk.inject.KAbstractModule

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

    config.keys?.forEach { key ->
      bind<Cipher>()
          .annotatedWith(Names.named(key.key_name))
          .toProvider(CipherProvider(config.kms_uri, key))
    }
  }

  private class CipherProvider(val keyUri: String, val key: Key) : Provider<Cipher> {
    @Inject lateinit var keyManager: KeyManager
    @Inject lateinit var kmsClient: KmsClient

    override fun get(): Cipher {
      val cipher = readKey(key.encrypted_key, kmsClient.getAead(keyUri))
      keyManager[key.key_name] = cipher
      return cipher
    }

    private fun readKey(keyConfig: Secret<String>, masterKey: Aead): Cipher {
      val keysetHandle = KeysetHandle.read(JsonKeysetReader.withString(keyConfig.value), masterKey)
      return RealCipher(keysetHandle, masterKey)
    }
  }
}