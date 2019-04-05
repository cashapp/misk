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

    config.keys.forEach { key ->
      bind<Cipher>()
          .annotatedWith(Names.named(key.key_name))
          .toProvider(CipherProvider(key.key_name, key.details))
          .asEagerSingleton()
    }
  }

  private class CipherProvider(val keyName: String, val keyDetails: List<EncryptedKey>) : Provider<Cipher> {
    @Inject lateinit var keyManager: KeyManager
    @Inject lateinit var kmsClient: KmsClient

    override fun get(): Cipher {
      val pairs = keyDetails.map { encryptedKeySpec ->
        val keyUri = getMasterKeyUri(encryptedKeySpec)
        val masterKey = kmsClient.getAead(keyUri)
        val keysetHandle = readKey(encryptedKeySpec.json_key_spec, masterKey)
        Pair(keysetHandle, masterKey)
      }
      val cipher = RealCipher(pairs)
      keyManager[keyName] = cipher
      return cipher
    }

    private fun readKey(keyConfig: Secret<String>, masterKey: Aead): KeysetHandle {
      return KeysetHandle.read(JsonKeysetReader.withString(keyConfig.value), masterKey)
    }

    private fun getMasterKeyUri(keyDetails: EncryptedKey): String {
      check(!(keyDetails.gcp_key_uri != null && keyDetails.aws_kms_key_alias != null))
      check(!(keyDetails.gcp_key_uri == null && keyDetails.aws_kms_key_alias == null))
      return  keyDetails.gcp_key_uri ?: "aws-kms://alias/${keyDetails.aws_kms_key_alias}"
    }
  }
}