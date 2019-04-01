package misk.crypto

import com.google.crypto.tink.KmsClient
import com.google.inject.Provides
import com.google.inject.Singleton
import misk.inject.KAbstractModule

/**
 * Test module that binds [FakeKmsClient] to be used as the [KmsClient]
 */
class CryptoTestModule : KAbstractModule() {

  override fun configure() {
    install(object : KAbstractModule() {
      @Provides @Singleton
      fun getKmsClient(): KmsClient {
        return FakeKmsClient()
      }
    })
  }
}