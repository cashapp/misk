package misk.crypto.testing

import misk.crypto.CryptoConfig
import misk.crypto.KeyResolver
import misk.inject.KAbstractModule
import wisp.deployment.Deployment

class FakeExternalKeyManagerModule(private val config: CryptoConfig) : KAbstractModule() {

  override fun configure() {
    requireBinding(Deployment::class.java)
    bind<KeyResolver>().toInstance(
      FakeKeyResolver(config.external_data_keys.orEmpty())
    )
  }
}
