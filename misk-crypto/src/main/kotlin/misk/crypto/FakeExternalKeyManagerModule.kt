package misk.crypto

import com.google.inject.Provides
import com.squareup.skim.crypto.FakeExternalKeyManager
import misk.environment.Deployment
import misk.inject.KAbstractModule

class FakeExternalKeyManagerModule(private val config: CryptoConfig) : KAbstractModule() {

  override fun configure() {
    requireBinding(Deployment::class.java)
  }

  @Provides
  fun provideExternalKeyManager(): ExternalKeyManager {
    return FakeExternalKeyManager(config.external_data_keys.orEmpty())
  }
}
