package com.squareup.chat

import misk.MiskApplication
import misk.MiskRealServiceModule
import misk.config.ConfigModule
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.eventrouter.RealEventRouterModule
import misk.metrics.backends.prometheus.PrometheusMetricsServiceModule
import misk.web.MiskWebModule

fun main(args: Array<String>) {
  val deployment = wisp.deployment.Deployment(name = "exemplarchat", isLocalDevelopment = true)

  val config = MiskConfig.load<ChatConfig>("chat", deployment)

  MiskApplication(
    MiskRealServiceModule(),
    MiskWebModule(config.web),
    RealEventRouterModule(deployment),
    ChatModule(),
    ConfigModule.create("chat", config),
    DeploymentModule(deployment),
    PrometheusMetricsServiceModule(config.prometheus)
  ).run(args)
}
