package misk.crypto

import com.google.crypto.tink.KmsClient
import com.google.inject.Provides
import com.google.inject.Singleton
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.daead.DeterministicAeadConfig
import com.google.crypto.tink.mac.MacConfig
import com.google.crypto.tink.signature.SignatureConfig
import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.tokens.FakeTokenGeneratorModule

/**
 * Test module that binds [FakeKmsClient] to be used as the [KmsClient]
 */
class CryptoTestModule : KAbstractModule() {
  override fun configure() {
    install(Modules.override(MiskTestingServiceModule()).with(FakeTokenGeneratorModule()))
    install(LogCollectorModule())
    install(object : KAbstractModule() {
      @Provides @Singleton
      fun getKmsClient(): KmsClient {
        return FakeKmsClient()
      }
    })

    AeadConfig.register()
    DeterministicAeadConfig.register()
    MacConfig.register()
    SignatureConfig.register()
  }
}