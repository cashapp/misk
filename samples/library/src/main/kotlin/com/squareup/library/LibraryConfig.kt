package com.squareup.library

import misk.jdbc.DataSourceClustersConfig
import misk.metrics.backends.prometheus.PrometheusConfig
import misk.web.WebConfig
import wisp.config.Config

data class LibraryConfig(
  val web: WebConfig,
  val prometheus: PrometheusConfig,
  val data_source_clusters: DataSourceClustersConfig,
  ) : Config
