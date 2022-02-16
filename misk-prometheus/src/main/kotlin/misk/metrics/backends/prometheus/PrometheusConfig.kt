package misk.metrics.backends.prometheus

import wisp.config.Config

/** Configuration for exporting metrics to prometheus */
data class PrometheusConfig(
  // The hostname on which metrics are exposed; if null uses any addr bound to this host
  val hostname: String? = null,
  // The port on metrics are exposed
  val http_port: Int,
  // How long observations are kept before they are discarded. Only used for Summary.
  val max_age_in_seconds: Long? = null,
) : Config
