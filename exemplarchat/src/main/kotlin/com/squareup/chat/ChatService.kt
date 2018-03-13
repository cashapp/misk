package com.squareup.chat

import misk.MiskApplication
import misk.MiskModule
import misk.config.ConfigModule
import misk.config.MiskConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.hibernate.HibernateModule
import misk.web.WebModule

fun main(args: Array<String>) {
  val environment = Environment.fromEnvironmentVariable()
  val config = MiskConfig.load<ChatConfig>("chat", environment)

  MiskApplication(
      MiskModule(),
      WebModule(),
      HibernateModule(),
      ChatModule(),
      ConfigModule.create("chat", config),
      EnvironmentModule(environment)
  ).run(args)
}
