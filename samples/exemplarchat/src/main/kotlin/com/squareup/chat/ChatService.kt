package com.squareup.chat

import misk.MiskApplication
import misk.MiskRealServiceModule
import misk.client.HttpClientsConfig
import misk.config.ConfigModule
import misk.environment.DeploymentModule
import misk.eventrouter.RealEventRouterModule
import misk.metrics.backends.prometheus.PrometheusMetricsServiceModule
import misk.web.MiskWebModule
import wisp.config.ConfigSource
import wisp.config.WispConfig
import wisp.config.addWispConfigSources

fun main(args: Array<String>) {
  val deployment = wisp.deployment.Deployment(name = "exemplarchat", isLocalDevelopment = true)
  val config = WispConfig.builder().addWispConfigSources(
    listOf(
      ConfigSource("classpath:/chat-deployment.yaml"),
    )
  ).build().loadConfigOrThrow<ChatConfig>()

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
