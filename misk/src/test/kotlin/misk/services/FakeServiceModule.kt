package misk.services

import com.google.common.util.concurrent.Service
import com.google.inject.AbstractModule
import misk.inject.addMultibinderBinding
import misk.inject.to

class FakeServiceModule : AbstractModule() {
  override fun configure() {
    binder().addMultibinderBinding<Service>()
        .to<FakeService>()
  }
}
