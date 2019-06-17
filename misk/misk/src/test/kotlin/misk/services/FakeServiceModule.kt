package misk.services

import misk.ServiceModule
import misk.inject.KAbstractModule

class FakeServiceModule : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<FakeService>())
  }
}
