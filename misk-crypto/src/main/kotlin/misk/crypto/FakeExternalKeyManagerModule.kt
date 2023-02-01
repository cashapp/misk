package misk.crypto

import misk.inject.KAbstractModule
import wisp.deployment.Deployment

@Deprecated("Use misk-crypto-testing instead",
  replaceWith = ReplaceWith("FakeExternalKeyManagerModule", imports = ["misk.crypto.testing"]))
class FakeExternalKeyManagerModule(private val config: CryptoConfig) : KAbstractModule() {

  override fun configure() {
    requireBinding(Deployment::class.java)
    bind<KeyResolver>().toInstance(
      FakeKeyResolver(config.external_data_keys.orEmpty())
    )
  }
}
