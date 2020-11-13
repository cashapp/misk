package com.squareup.exemplar

import misk.MiskApplication
import misk.MiskRealServiceModule
import misk.config.ConfigModule
import misk.config.MiskConfig
import misk.environment.Deployment
import misk.environment.DeploymentModule
import misk.environment.Env
import misk.environment.Environment
import misk.metrics.backends.prometheus.PrometheusMetricsServiceModule
import misk.web.MiskWebModule

fun main(args: Array<String>) {
  val environment = Env(Environment.fromEnvironmentVariable().name)
  val deployment = Deployment(name = "exemplar", isLocalDevelopment = true)
  val config = MiskConfig.load<ExemplarConfig>("exemplar", environment)
  MiskApplication(
      MiskRealServiceModule(),
      MiskWebModule(config.web),
      ExemplarModule(),
      ConfigModule.create("exemplar", config),
      DeploymentModule(deployment, environment),
      PrometheusMetricsServiceModule(config.prometheus)
  ).run(args)
}
