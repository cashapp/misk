package misk.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.BinaryKeysetReader
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.KmsClient
import com.google.crypto.tink.aead.AeadConfig
import com.google.inject.name.Names
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
    for (keyConfig: Key in config.keys) {
      val keyUri = config.gcp_key_uri ?: "aws-kms://alias/${config.aws_kms_key_alias}"
      val cipher = keyConfig.encrypted_key.let { readKey(it, kmsClient.getAead(keyUri)) }
      keyManager[keyConfig.key_name] = cipher
      bind<Cipher>().annotatedWith(Names.named(keyConfig.key_name)).toInstance(cipher)
    }
  }

  private fun readKey(keyConfig: String, masterKey: Aead): Cipher {
    val encryptedKek = Base64.getDecoder().decode(keyConfig)
    val keysetHandle = KeysetHandle.read(BinaryKeysetReader.withBytes(encryptedKek), masterKey)
    return RealCipher(keysetHandle, masterKey)
  }
}