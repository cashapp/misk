package com.squareup.exemplar.healthchecks

import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import misk.web.actions.StatusActionApi
import misk.web.jetty.WsConnections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ManualHealthCheck : HealthCheck {
  @Inject lateinit var wsConnections: WsConnections

  private var healthStatus =
      HealthStatus.unhealthy("I have not been toggled yet")

  override fun status(): HealthStatus {
    return healthStatus
  }

  fun toggle(): Pair<HealthStatus, HealthStatus> {
    val before = this.healthStatus
    val after = if (before.isHealthy)
      HealthStatus.unhealthy("I have been toggled to unhealthy")
    else
      HealthStatus.healthy("I have been toggled healthy")

    this.healthStatus = after
    val event = StatusActionApi.Companion.HealthStatus(
        this.javaClass.name, after.isHealthy, after.messages
    )
    wsConnections.sendJson(event, "all")
    return Pair(before, after)
  }
}
