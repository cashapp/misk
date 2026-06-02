package com.squareup.chat.healthchecks

import jakarta.inject.Singleton
import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus

@Singleton
class ManualHealthCheck : HealthCheck {
  private var healthy = true

  override fun status(): HealthStatus {
    return if (healthy) {
      HealthStatus.healthy()
    } else {
      HealthStatus.unhealthy("Manually set to unhealthy")
    }
  }

  fun setHealth() {
    healthy = true
  }

  fun setUnhealthy() {
    healthy = false
  }
}
