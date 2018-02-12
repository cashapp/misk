package com.squareup.exemplar

import com.google.inject.AbstractModule
import com.squareup.exemplar.actions.HelloWebAction
import com.squareup.exemplar.actions.HelloWebPostAction
import com.squareup.exemplar.actions.ToggleManualHealthCheckAction
import com.squareup.exemplar.healthchecks.ManualHealthCheck
import misk.healthchecks.HealthChecksModule
import misk.metrics.web.MetricsJsonAction
import misk.web.WebActionModule
import misk.web.actions.InternalErrorAction
import misk.web.actions.LivenessCheckAction
import misk.web.actions.NotFoundAction
import misk.web.actions.ReadinessCheckAction
import misk.web.actions.StatusAction
import misk.web.actions.StatusActionApi

class ExemplarModule : AbstractModule() {
  override fun configure() {
    install(WebActionModule.create<HelloWebAction>())
    install(WebActionModule.create<HelloWebPostAction>())
    install(WebActionModule.create<MetricsJsonAction>())
    install(WebActionModule.create<InternalErrorAction>())
    install(WebActionModule.create<ReadinessCheckAction>())
    install(WebActionModule.create<LivenessCheckAction>())
    install(WebActionModule.create<StatusAction>())
    install(WebActionModule.create<StatusActionApi>())
    install(WebActionModule.create<ToggleManualHealthCheckAction>())
    install(WebActionModule.create<NotFoundAction>())

    install(HealthChecksModule(ManualHealthCheck::class.java))
  }
}
