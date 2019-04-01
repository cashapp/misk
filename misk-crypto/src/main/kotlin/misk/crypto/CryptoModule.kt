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
import misk.inject.asSingleton

/**
 * Configures and registers the keys listed in the configuration file.
 * Each key is read, decrypted, and then bound via Google Guice and added to the [KeyManager].
 */
class CryptoModule(
  private val config: CryptoConfig
) : KAbstractModule() {

  override fun configure() {
    requireBinding(KmsClient::class.java)
    check(!(config.gcp_key_uri != null && config.aws_kms_key_alias != null))
    check(!(config.gcp_key_uri == null && config.aws_kms_key_alias == null))
    AeadConfig.register()

    bind<KeyManager>().asSingleton()
    config.keys?.forEach { key ->
      val keyUri = config.gcp_key_uri ?: "aws-kms://alias/${config.aws_kms_key_alias}"
      bind<Cipher>()
          .annotatedWith(Names.named(key.key_name))
          .toProvider(CipherProvider(keyUri, key))
          .asEagerSingleton()
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