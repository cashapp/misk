package misk.metrics.backends.prometheus

import wisp.config.Config

/** Configuration for exporting metrics to prometheus */
data class PrometheusConfig(
  // The hostname on which metrics are exposed; if null uses any addr bound to this host
  val hostname: String? = null,
  val http_port: Int // The port on metrics are exposed
) : Config
