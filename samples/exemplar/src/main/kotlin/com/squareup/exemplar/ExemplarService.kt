package com.squareup.exemplar

import com.squareup.exemplar.dashboard.ExemplarDashboardModule
import com.squareup.exemplar.events.EventServiceModule
import misk.MiskApplication
import misk.MiskRealServiceModule
import misk.config.ConfigModule
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.metrics.backends.prometheus.PrometheusMetricsServiceModule
import misk.monitoring.MonitoringModule
import misk.web.MiskWebModule
import wisp.deployment.Deployment

fun main(args: Array<String>) {
  ExemplarLogging.configure()
  val deployment = Deployment(name = "exemplar", isLocalDevelopment = true)
  val config = MiskConfig.load<ExemplarConfig>("exemplar", deployment)
  MiskApplication(
    ConfigModule.create("exemplar", config),
    DeploymentModule(deployment),
    ExemplarAccessModule(),
    ExemplarDashboardModule(),
    ExemplarWebActionsModule(),
    MiskRealServiceModule(),
    EventServiceModule(),
    MiskWebModule(config.web),
    PrometheusMetricsServiceModule(config.prometheus),
    MonitoringModule(),
  ).run(args)
}
