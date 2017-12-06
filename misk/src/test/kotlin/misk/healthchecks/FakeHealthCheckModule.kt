package misk.healthchecks

import com.google.inject.AbstractModule
import misk.inject.newMultibinder
import misk.inject.to

class FakeHealthCheckModule : AbstractModule() {
  override fun configure() {
    binder().newMultibinder<HealthCheck>().to<FakeHealthCheck>()
  }
}
