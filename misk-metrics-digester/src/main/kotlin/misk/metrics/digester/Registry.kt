package misk.metrics.digester

import misk.metrics.Histogram
import misk.metrics.HistogramRecordMetric
import misk.metrics.HistogramRegistry
import java.util.Objects
import java.util.concurrent.ConcurrentHashMap

/** DigestMetric contains the contents of an individual metrics within a histogram */
class DigestMetric(
  val digest: SlidingWindowDigest<*>,
  val labels: List<String>
) {
  /** Adds an observation to the metric */
  fun observe(value: Double) {
    digest.observe(value)
  }
}

class TDigestHistogramRecordMetric constructor(private val metric: DigestMetric) :
    HistogramRecordMetric {
  override fun observe(duration: Double) {
    metric.observe(duration)
  }
}

/** TDigestHistogram stores histograms which records metrics */
class TDigestHistogram<T : TDigest<T>> constructor(
  val name: String,
  val help: String,
  val quantiles: List<Double>,
  private val tDigest: () -> SlidingWindowDigest<T>
) : Histogram {

  private val metrics = ConcurrentHashMap<Int, DigestMetric>()

  /** Records a new metric within the histogram */
  override fun record(vararg labelValues: String): TDigestHistogramRecordMetric {
    val metric = metrics.getOrPut(key(labelValues)) {
      DigestMetric(tDigest(), labelValues.asList())
    }
    return TDigestHistogramRecordMetric(metric)
  }

  /** Returns the number of windows within the histogram */
  override fun count(vararg labelValues: String): Int {
    return metrics.get(key(labelValues))?.digest?.windows?.count() ?: 0
  }

  /** Returns a metric of the histogram. Order of labels matters */
  fun getMetric(vararg metric: String): DigestMetric? {
    return metrics[key(metric)]
  }

  /** Creates a hash for a list of labels */
  private fun key(labels: Array<out String>) = Objects.hash(labels.sorted())
}

/** Default quantiles that can be used to register with a new histogram */
val defaultQuantiles =
    mapOf(0.5 to 0.05, 0.75 to 0.02, 0.95 to 0.01, 0.99 to 0.001, 0.999 to 0.0001)

/** TDigestHistogramRegistry registers all TDigestHistograms */
class TDigestHistogramRegistry<T : TDigest<T>> constructor(
  private val newDigestFn: () -> SlidingWindowDigest<T>
) : HistogramRegistry {

  companion object {
    fun newVeneurRegistry(): TDigestHistogramRegistry<VeneurDigest> {
      return TDigestHistogramRegistry(
          fun() = SlidingWindowDigest(
              Windower(windowSecs = 30, stagger = 3),
              fun() = VeneurDigest())
      )
    }
  }

  private val histograms = ConcurrentHashMap<String, TDigestHistogram<T>>()

  /** Creates and returns a new histogram. Histogram is registered within the Registry */
  override fun newHistogram(
    name: String,
    help: String,
    labelNames: List<String>,
    quantiles: Map<Double, Double>
  ): TDigestHistogram<T> {
    val histogram = TDigestHistogram(name, help, quantiles.keys.toList(), fun() = newDigestFn())
    return register(histogram)
  }

  /** Registers a histogram within the Registry */
  @Synchronized private fun register(histogram: TDigestHistogram<T>): TDigestHistogram<T> {
    return histograms.getOrPut(histogram.name) { histogram }
  }

  /** Returns a list of all Histograms registered by the Registry */
  fun allHistograms() = histograms.values

  /** Returns a histogram set under the given labels */
  fun getHistogram(labels: String) = histograms[labels]
}
