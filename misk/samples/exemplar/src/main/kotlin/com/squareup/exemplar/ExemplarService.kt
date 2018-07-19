package com.squareup.exemplar

import misk.MiskApplication
import misk.MiskServiceModule
import misk.config.ConfigModule
import misk.config.MiskConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.web.MiskWebModule

fun main(args: Array<String>) {
  val environment = Environment.fromEnvironmentVariable()
  val config = MiskConfig.load<ExemplarConfig>("exemplar", environment)

  MiskApplication(
      MiskServiceModule(),
      MiskWebModule(),
      ExemplarModule(),
      ConfigModule.create("exemplar", config),
      EnvironmentModule(environment)
  ).run(args)
}
