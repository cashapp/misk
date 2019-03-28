package misk.crypto

import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.inject.name.Names
import misk.inject.KAbstractModule

/**
 * This module should be used for testing purposes only.
 * It generates random keys for each key name specified in the configuration
 * and uses [FakeKmsClient] instead of a real KMS service.
 *
 * This module **will** read the exact same configuration files as the real application,
 * but **will not** use the key material specified in the configuration.
 * Instead, it'll generate a random keyset handle for each named key.
 */
class CryptoTestModule(
  private val config: CryptoConfig
) : KAbstractModule() {

  override fun configure() {
    AeadConfig.register()
    val keyManager = KeyManager()
    bind<KeyManager>().toInstance(keyManager)
    val masterKey = FakeKmsClient().getAead(config.gcp_key_uri ?: config.aws_kms_key_alias)
    config.keys?.forEach { key ->
      val keysetHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
      val cipher = RealCipher(keysetHandle, masterKey)
      keyManager[key.key_name] = cipher
      bind<Cipher>().annotatedWith(Names.named(key.key_name)).toInstance(cipher)
    }
  }
}