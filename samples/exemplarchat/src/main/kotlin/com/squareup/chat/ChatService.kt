package com.squareup.chat

import misk.MiskApplication
import misk.MiskRealServiceModule
import misk.config.ConfigModule
import misk.config.MiskConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.eventrouter.RealEventRouterModule
import misk.metrics.backends.prometheus.PrometheusMetricsModule
import misk.web.MiskWebModule

fun main(args: Array<String>) {
  val environment = Environment.fromEnvironmentVariable()
  val config = MiskConfig.load<ChatConfig>("chat", environment.name)

  MiskApplication(
      MiskRealServiceModule(),
      MiskWebModule(config.web),
      RealEventRouterModule(environment),
      ChatModule(),
      ConfigModule.create("chat", config),
      EnvironmentModule(environment),
      PrometheusMetricsModule(config.prometheus)
  ).run(args)
}
