package misk.metrics

/**
 * Skeleton for the functionality of histograms
 *
 * A histogram samples observations (usually things like request durations or response sizes)
 * and counts them in configurable buckets.
 *
 * A sample implementation can be found in PrometheusHistogram
 */
interface Histogram {
  /** records a new set of labels and accompanying duration */
  fun labels(vararg labelValues: String): HistogramRecordMetric

  /** returns the number of buckets */
  fun count(vararg labelValues: String): Int
}

/** HistogramRecordMetric provides the metric to record an observation */
interface HistogramRecordMetric {
  fun observe(duration: Double)
}