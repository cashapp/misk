package misk.services

import com.google.common.util.concurrent.Service
import misk.inject.KAbstractModule

class FakeServiceModule : KAbstractModule() {
  override fun configure() {
    multibind<Service>().to<FakeService>()
  }
}
