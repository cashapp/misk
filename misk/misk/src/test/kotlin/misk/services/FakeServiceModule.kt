package misk.services

import com.google.common.util.concurrent.Service
import com.google.inject.AbstractModule
import misk.inject.asSingleton
import misk.inject.newMultibinder
import misk.inject.to

class FakeServiceModule : AbstractModule() {
  override fun configure() {
    binder().newMultibinder<Service>().to<FakeService>()
  }
}
