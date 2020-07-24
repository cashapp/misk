package misk.metrics.backends.prometheus

import misk.config.Config

/** Configuration for exporting metrics to prometheus */
data class PrometheusConfig(
  val hostname: String? = null, // The hostname on which metrics are exposed; if null uses any addr bound to this host
  val http_port: Int // The port on metrics are exposed
) : Config
