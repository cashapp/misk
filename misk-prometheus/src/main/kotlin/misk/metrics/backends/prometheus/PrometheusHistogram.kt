package misk.metrics.backends.prometheus

import io.prometheus.client.Summary as PrometheusSummary
import misk.metrics.Histogram

/**
 * PrometheusHistogram implements `Histogram` interface with prometheus `Summary` type.
 */
class PrometheusHistogram(
  histogram: PrometheusSummary
) : Histogram(histogram)
