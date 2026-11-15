package com.squareup.exemplar

import misk.audit.AuditClientConfig
import misk.config.Config
import misk.config.Redact
import misk.config.Secret
import misk.jdbc.DataSourceClustersConfig
import misk.metrics.backends.prometheus.PrometheusConfig
import misk.web.WebConfig

data class ExemplarConfig(
  val apiKey: Secret<String>,
  val web: WebConfig,
  val prometheus: PrometheusConfig,
  @Redact val redacted: String,
  val audit: AuditClientConfig,
  val data_source_clusters: DataSourceClustersConfig,
) : Config
