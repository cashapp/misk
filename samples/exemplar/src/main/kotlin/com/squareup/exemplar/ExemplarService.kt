package com.squareup.exemplar

import misk.MiskApplication
import misk.MiskRealServiceModule
import misk.config.ConfigModule
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.metrics.backends.prometheus.PrometheusMetricsServiceModule
import misk.tokens.TokenGeneratorModule
import misk.perf.PauseDetectorConfig
import misk.perf.PauseDetectorModule
import misk.web.MiskWebModule
import wisp.deployment.Deployment

fun main(args: Array<String>) {
  val deployment = Deployment(name = "exemplar", isLocalDevelopment = true)
  val config = MiskConfig.load<ExemplarConfig>("exemplar", deployment)
  MiskApplication(
    ConfigModule.create("exemplar", config),
    DashboardModule(),
    DeploymentModule(deployment),
    ExemplarAccessModule(),
    ExemplarWebActionsModule(),
    MiskRealServiceModule(),
    MiskWebModule(config.web),
    PrometheusMetricsServiceModule(config.prometheus),
    TokenGeneratorModule(),
    PauseDetectorModule(PauseDetectorConfig(logInfoMillis = 100))
  ).run(args)
}
