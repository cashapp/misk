package misk.metrics.backends.prometheus

import wisp.config.Config
import javax.inject.Inject

/** Configuration for exporting metrics to prometheus */
data class PrometheusConfig(
  // The hostname on which metrics are exposed; if null uses any addr bound to this host
  val hostname: String? = null,
  // The port on metrics are exposed
  val http_port: Int = 9102,
  // How long observations are kept before they are discarded. Only used for Summary.
  val max_age_in_seconds: Long? = null,
) : Config {
  @Inject constructor() : this(
    hostname = null,
    http_port = 9102,
    max_age_in_seconds = null,
  )
}
