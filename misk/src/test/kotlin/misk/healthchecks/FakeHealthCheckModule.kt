package misk.healthchecks

import com.google.inject.AbstractModule
import misk.inject.addMultibinderBinding
import misk.inject.to

class FakeHealthCheckModule : AbstractModule() {
  override fun configure() {
    binder().addMultibinderBinding<HealthCheck>()
        .to<FakeHealthCheck>()
  }
}
