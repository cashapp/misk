package com.squareup.library

import com.squareup.library.persistence.LibraryPersistenceModule
import misk.MiskApplication
import misk.MiskRealServiceModule
import misk.config.ConfigModule
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.metrics.backends.prometheus.PrometheusMetricsServiceModule
import misk.web.MiskWebModule
import misk.web.dashboard.ConfigDashboardTabModule
import wisp.deployment.Deployment

fun main(args: Array<String>) {
  val deployment = Deployment(name = "library", isLocalDevelopment = true)
  val config = MiskConfig.load<LibraryConfig>("library", deployment)
  MiskApplication(
    MiskRealServiceModule(),
    MiskWebModule(config.web),
    LibraryAccessModule(),
    LibraryWebActionsModule(),
    ConfigModule.create("library", config),
    DeploymentModule(deployment),
    PrometheusMetricsServiceModule(config.prometheus),
    DashboardModule(),
    ConfigDashboardTabModule(isDevelopment = true),
    LibraryPersistenceModule(config)
  ).run(args)
}
