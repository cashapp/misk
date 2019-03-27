package misk.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.BinaryKeysetReader
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.KmsClient
import com.google.crypto.tink.aead.AeadConfig
import com.google.inject.name.Names
import misk.config.Secret
import misk.inject.KAbstractModule
import java.util.Base64

/**
 * Configures and registers the keys listed in the configuration file.
 * Each key is read, decrypted, and then bound via Google Guice and added to the [KeyManager].
 */
class CryptoModule(
  private val config: CryptoConfig,
  private val kmsClient: KmsClient
) : KAbstractModule() {

  override fun configure() {
    check(!(config.gcp_key_uri != null && config.aws_kms_key_alias != null))
    check(!(config.gcp_key_uri == null && config.aws_kms_key_alias == null))
    AeadConfig.register()
    val keyManager = KeyManager()
    bind<KeyManager>().toInstance(keyManager)
    config.keys?.forEach { key ->
      val keyUri = config.gcp_key_uri ?: "aws-kms://alias/${config.aws_kms_key_alias}"
      val cipher = key.encrypted_key.let { readKey(it, kmsClient.getAead(keyUri)) }
      keyManager[key.key_name] = cipher
      bind<Cipher>().annotatedWith(Names.named(key.key_name)).toInstance(cipher)
    }
  }

  private fun readKey(keyConfig: Secret<String>, masterKey: Aead): Cipher {
    val keysetHandle = KeysetHandle.read(JsonKeysetReader.withString(keyConfig.value), masterKey)
    return RealCipher(keysetHandle, masterKey)
  }
}