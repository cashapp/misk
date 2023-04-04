package com.squareup.chat

import misk.MiskApplication
import misk.MiskRealServiceModule
import misk.config.ConfigModule
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.metrics.backends.prometheus.PrometheusMetricsServiceModule
import misk.redis.RedisModule
import misk.redis.testing.DockerRedis
import misk.web.MiskWebModule
import redis.clients.jedis.JedisPoolConfig

fun main(args: Array<String>) {
  val deployment = wisp.deployment.Deployment(name = "exemplarchat", isLocalDevelopment = true)

  val config = MiskConfig.load<ChatConfig>("chat", deployment)

  MiskApplication(
    MiskRealServiceModule(),
    MiskWebModule(config.web),
    ChatModule(),
    RedisModule(DockerRedis.config, JedisPoolConfig(), useSsl = false),
    ConfigModule.create("chat", config),
    DeploymentModule(deployment),
    PrometheusMetricsServiceModule(config.prometheus)
  ).run(args)
}
