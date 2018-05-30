package com.squareup.exemplar

import misk.MiskApplication
import misk.MiskModule
import misk.config.ConfigModule
import misk.config.MiskConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.resources.ResourceLoaderModule
import misk.web.WebModule

fun main(args: Array<String>) {
  val environment = Environment.fromEnvironmentVariable()
  val config = MiskConfig.load<ExemplarConfig>("exemplar", environment)

  MiskApplication(
      MiskModule(),
      WebModule(),
      ExemplarModule(),
      ConfigModule.create("exemplar", config),
      EnvironmentModule(environment),
      ResourceLoaderModule()
  ).run(args)
}
