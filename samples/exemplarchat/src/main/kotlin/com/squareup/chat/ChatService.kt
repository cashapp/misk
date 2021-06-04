package com.squareup.chat

import misk.MiskApplication
import misk.MiskRealServiceModule
import misk.config.ConfigModule
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.environment.Env
import misk.eventrouter.RealEventRouterModule
import misk.metrics.backends.prometheus.PrometheusMetricsServiceModule
import misk.web.MiskWebModule

fun main(args: Array<String>) {
  val deployment = wisp.deployment.Deployment(name = "exemplarchat", isLocalDevelopment = true)
  val env = Env("development")

  val config = MiskConfig.load<ChatConfig>("chat", env)

  MiskApplication(
    MiskRealServiceModule(),
    MiskWebModule(config.web),
    RealEventRouterModule(deployment),
    ChatModule(),
    ConfigModule.create("chat", config),
    DeploymentModule(deployment, env),
    PrometheusMetricsServiceModule(config.prometheus)
  ).run(args)
}
