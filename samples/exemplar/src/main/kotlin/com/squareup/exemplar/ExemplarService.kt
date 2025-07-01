package com.squareup.exemplar

import com.squareup.exemplar.audit.ExemplarAuditClientModule
import com.squareup.exemplar.dashboard.ExemplarDashboardModule
import misk.MiskApplication
import misk.MiskRealServiceModule
import misk.annotation.ExperimentalMiskApi
import misk.config.ConfigModule
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.lease.mysql.SqlLeaseModule
import misk.metrics.backends.prometheus.PrometheusMetricsServiceModule
import misk.monitoring.MonitoringModule
import misk.web.MiskWebModule
import wisp.deployment.Deployment

@OptIn(ExperimentalMiskApi::class)
fun main(args: Array<String>) {
  ExemplarLogging.configure()
  val deployment = Deployment(name = "exemplar", isLocalDevelopment = true)
  val config = MiskConfig.load<ExemplarConfig>("exemplar", deployment)
  MiskApplication(
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
    .run(args)
}
