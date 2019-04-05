package misk.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.inject.name.Names
import misk.inject.KAbstractModule
import javax.inject.Inject
import javax.inject.Provider

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

    val masterKey = FakeKmsClient().getAead(null)
    config.keys.forEach { key ->
      bind<Cipher>()
          .annotatedWith(Names.named(key.key_name))
          .toProvider(CipherProvider(key.key_name, masterKey))
          .asEagerSingleton()
    }
  }

  private class CipherProvider(val keyName: String, val masterAead: Aead) : Provider<Cipher> {
    @Inject lateinit var keyManager: KeyManager

    override fun get(): Cipher {
      val keysetHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
      return  RealCipher(listOf(Pair(keysetHandle, masterAead)))
          .also { keyManager[keyName] = it }
    }
  }
}