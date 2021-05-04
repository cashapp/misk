package com.squareup.exemplar

import misk.MiskApplication
import misk.MiskRealServiceModule
import misk.config.ConfigModule
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.environment.Env
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.metrics.backends.prometheus.PrometheusMetricsServiceModule
import misk.web.MiskWebModule
import misk.web.dashboard.AdminDashboardModule
import misk.web.dashboard.ConfigDashboardTabModule
import wisp.deployment.Deployment

fun main(args: Array<String>) {
  val environment = Environment.fromEnvironmentVariable()
  val env = Env(environment.name)
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
    EnvironmentModule(environment = environment),
    AdminDashboardModule(
      isDevelopment = true,
      dashboardProtobufDocUrlPrefix = "https://example.com/"
    ),
    ConfigDashboardTabModule(isDevelopment = true)
  ).run(args)
}
