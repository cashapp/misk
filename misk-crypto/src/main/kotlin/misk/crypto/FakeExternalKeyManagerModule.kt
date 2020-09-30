package misk.crypto

import misk.environment.Deployment
import misk.inject.KAbstractModule

class FakeExternalKeyManagerModule(private val config: CryptoConfig) : KAbstractModule() {

  override fun configure() {
    requireBinding(Deployment::class.java)
    bind<ExternalKeyManager>().toInstance(FakeExternalKeyManager(config.external_data_keys.orEmpty()))
  }
}
