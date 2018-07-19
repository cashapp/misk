package com.squareup.chat

import misk.MiskApplication
import misk.MiskServiceModule
import misk.config.ConfigModule
import misk.config.MiskConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.eventrouter.RealEventRouterModule
import misk.web.MiskWebModule

fun main(args: Array<String>) {
  val environment = Environment.fromEnvironmentVariable()
  val config = MiskConfig.load<ChatConfig>("chat", environment)

  MiskApplication(
      MiskServiceModule(),
      MiskWebModule(),
      RealEventRouterModule(environment),
      ChatModule(),
      ConfigModule.create("chat", config),
      EnvironmentModule(environment)
  ).run(args)
}
