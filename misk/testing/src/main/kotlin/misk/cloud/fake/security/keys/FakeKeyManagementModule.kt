package misk.cloud.fake.security.keys

import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.security.keys.KeyService

class FakeKeyManagementModule : KAbstractModule() {
  override fun configure() {
    bind<KeyService>().to<FakeKeyService>()
        .asSingleton()
  }
}
