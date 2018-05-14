package com.squareup.chat.healthchecks

import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import javax.inject.Singleton

@Singleton
class ManualHealthCheck : HealthCheck {
  private var healthy = true

  override fun status(): HealthStatus {
    return if (healthy) HealthStatus.healthy() else HealthStatus.unhealthy()
  }

  fun setHealth() {
    healthy = true
  }

  fun setUnhealthy() {
    healthy = false
  }
}
