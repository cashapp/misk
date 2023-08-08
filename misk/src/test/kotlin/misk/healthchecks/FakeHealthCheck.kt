package misk.healthchecks

import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class FakeHealthCheck @Inject constructor() : HealthCheck {
  var status = HealthStatus.healthy()

  fun setHealthy(vararg messages: String) {
    status = HealthStatus.healthy(*messages)
  }

  fun setUnhealthy(vararg messages: String) {
    status = HealthStatus.unhealthy(*messages)
  }

  override fun status(): HealthStatus = status
}
