package com.squareup.exemplar

import misk.MiskApplication
import misk.MiskRealServiceModule
import misk.client.HttpClientsConfig
import misk.config.ConfigModule
import misk.environment.DeploymentModule
import misk.metrics.backends.prometheus.PrometheusMetricsServiceModule
import misk.web.MiskWebModule
import misk.web.dashboard.ConfigDashboardTabModule
import wisp.config.ConfigSource
import wisp.config.WispConfig
import wisp.config.addWispConfigSources
import wisp.deployment.Deployment

fun main(args: Array<String>) {
  val deployment = Deployment(name = "exemplar", isLocalDevelopment = true)
  val config = WispConfig.builder().addWispConfigSources(
    listOf(
      ConfigSource("classpath:/exemplar-deployment.yaml"),
    )
  ).build().loadConfigOrThrow<ExemplarConfig>()
  MiskApplication(
    MiskRealServiceModule(),
    MiskWebModule(config.web),
    ExemplarAccessModule(),
    ExemplarWebActionsModule(),
    ConfigModule.create("exemplar", config),
    DeploymentModule(deployment),
    PrometheusMetricsServiceModule(config.prometheus),
    DashboardModule(),
  ).run(args)
}
