package com.squareup.exemplar

import misk.MiskApplication
import misk.MiskRealServiceModule
import misk.config.ConfigModule
import misk.config.MiskConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.metrics.backends.prometheus.PrometheusMetricsModule
import misk.web.MiskWebModule

fun main(args: Array<String>) {
  val environment = Environment.fromEnvironmentVariable()
  val config = MiskConfig.load<ExemplarConfig>("exemplar", environment)

  MiskApplication(
      MiskRealServiceModule(),
      MiskWebModule(config.web),
      ExemplarModule(),
      ConfigModule.create("exemplar", config),
      EnvironmentModule(environment),
      PrometheusMetricsModule(config.prometheus)
  ).run(args)
}
