package misk.healthchecks

import misk.web.actions.ReadinessCheckAction

/**
 * Allows users to define custom health checks. An app with a failing HealthCheck will fail the
 * readiness check in [ReadinessCheckAction], indicating that the app should not accept traffic.
 */
interface HealthCheck {
  /**
   * Computes whether a component of an application is healthy. For example, an implementing class
   * can check database connectivity.
   */
  fun status(): HealthStatus
}

data class HealthStatus(
  val isHealthy: Boolean,
  val messages: List<String>
) {
  companion object {
    fun healthy(vararg messages: String) = HealthStatus(true, messages.asList())

    fun unhealthy(vararg messages: String) = HealthStatus(false, messages.asList())
  }
}
