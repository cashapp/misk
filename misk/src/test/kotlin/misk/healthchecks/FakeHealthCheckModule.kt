package misk.healthchecks

import misk.inject.KAbstractModule

class FakeHealthCheckModule : KAbstractModule() {
  override fun configure() {
    multibind<HealthCheck>().to<FakeHealthCheck>()
  }
}
