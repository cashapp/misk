package com.squareup.exemplar

import misk.MiskApplication
import misk.MiskRealServiceModule
import misk.config.ConfigModule
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.environment.Env
import misk.metrics.backends.prometheus.PrometheusMetricsServiceModule
import misk.web.MiskWebModule
import misk.web.dashboard.ConfigDashboardTabModule
import wisp.deployment.Deployment

fun main(args: Array<String>) {
  val env = Env("development")
  val deployment = Deployment(name = "exemplar", isLocalDevelopment = true)
  val config = MiskConfig.load<ExemplarConfig>("exemplar", env)
  MiskApplication(
    MiskRealServiceModule(),
    MiskWebModule(config.web),
    ExemplarAccessModule(),
    ExemplarWebActionsModule(),
    ConfigModule.create("exemplar", config),
    DeploymentModule(deployment, env),
    PrometheusMetricsServiceModule(config.prometheus),
    DashboardModule(),
    ConfigDashboardTabModule(isDevelopment = true)
  ).run(args)
}
