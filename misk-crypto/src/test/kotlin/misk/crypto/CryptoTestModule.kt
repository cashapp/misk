package misk.crypto

import com.google.crypto.tink.KmsClient
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.daead.DeterministicAeadConfig
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.mac.MacConfig
import com.google.crypto.tink.signature.SignatureConfig
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import com.google.inject.Provides
import com.google.inject.Singleton
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule

/**
 * Test module that binds [FakeKmsClient] to be used as the [KmsClient]
 */
class CryptoTestModule : KAbstractModule() {
  override fun configure() {
    install(MiskTestingServiceModule())
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
    HybridConfig.register()
    StreamingAeadConfig.register()
  }
}
