package misk.metrics.backends.prometheus

import jakarta.inject.Inject
import misk.config.Config

/** Configuration for exporting metrics to prometheus */
@Suppress("AnnotatePublicApisWithJvmOverloads")
data class PrometheusConfig
constructor(
  // The hostname on which metrics are exposed; if null uses any addr bound to this host
  val hostname: String? = null,
  // The port on metrics are exposed
  val http_port: Int = 9102,
  // How long observations are kept before they are discarded. Only used for Summary.
  val max_age_in_seconds: Long? = null,
  // Disable recording Summary metrics where a Histogram counterpart is available.
  val disable_default_summary_metrics: Boolean = false,
) : Config {
  @Inject
  constructor() :
    this(hostname = null, http_port = 9102, max_age_in_seconds = null, disable_default_summary_metrics = false)
}
