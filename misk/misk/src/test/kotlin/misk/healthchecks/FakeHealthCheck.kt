package misk.healthchecks

import javax.inject.Singleton

@Singleton
class FakeHealthCheck : HealthCheck {
  var status = HealthStatus.healthy()

  fun setHealthy(vararg messages: String) {
    status = HealthStatus.healthy(*messages)
  }

  fun setUnhealthy(vararg messages: String) {
    status = HealthStatus.unhealthy(*messages)
  }

  override fun status(): HealthStatus = status
}
