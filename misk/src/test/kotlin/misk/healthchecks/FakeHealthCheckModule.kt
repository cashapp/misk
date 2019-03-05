package misk.healthchecks

import misk.inject.KAbstractModule

class FakeHealthCheckModule : KAbstractModule() {
  override fun configure() {
    bind<FakeHealthCheck>()
    multibind<HealthCheck>().to<FakeHealthCheck>()
  }
}
