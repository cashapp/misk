@file:OptIn(ExperimentalMiskApi::class)

package com.squareup.exemplar

import com.squareup.exemplar.audit.ExemplarAuditClientModule
import com.squareup.exemplar.dashboard.ExemplarDashboardModule
import misk.MiskApplication
import misk.MiskRealServiceModule
import misk.annotation.ExperimentalMiskApi
import misk.config.ConfigModule
import misk.config.MiskConfig
import misk.dev.runDevApplication
import misk.environment.DeploymentModule
import misk.lease.mysql.SqlLeaseModule
import misk.metrics.backends.prometheus.PrometheusMetricsServiceModule
import misk.monitoring.MonitoringModule
import misk.web.MiskWebModule
import wisp.deployment.Deployment

fun main(args: Array<String>) {
  ExemplarLogging.configure()
  runDevApplication(::application)
}

fun application(): MiskApplication {
  val deployment = Deployment(name = "exemplar", isLocalDevelopment = true)
  val config = MiskConfig.load<ExemplarConfig>("exemplar", deployment)
  val modules =
    listOf(
      ConfigModule.create("exemplar", config),
      DeploymentModule(deployment),
      ExemplarAccessModule(),
      ExemplarAuditClientModule(config.audit),
      ExemplarDashboardModule(deployment),
      ExemplarMetadataModule(),
      ExemplarWebActionsModule(),
      ExemplarCronModule(),
      ExemplarGuiceBindingsModule(),
      MiskRealServiceModule(),
      MiskWebModule(config.web),
      PrometheusMetricsServiceModule(config.prometheus),
      MonitoringModule(),
      SqlLeaseModule(config.data_source_clusters),
    )

  return MiskApplication(modules)
}
