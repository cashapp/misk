package misk.metrics.digester

import misk.metrics.Histogram
import misk.metrics.HistogramRegistry

/** DigestMetric contains the contents of an individual metrics within a histogram */
class DigestMetric(
  internal val digest: SlidingWindowDigest<*>,
  private val labels: List<String>
) {
  /** Adds an observation to the metric */
  fun observe(value: Double) {
    digest.observe(value)
  }

  /** Returns the labels for the metric */
  fun labels(): List<String> {
    return labels
  }

  /** Returns the SlidingWindowDigest registered for the metric */
  fun digest(): SlidingWindowDigest<*> {
    return digest
  }
}

/** TDigestHistogram stores histograms which records metrics */
class TDigestHistogram <T : TDigest<T>> constructor(
  private val name: String,
  private val help: String,
  private val quantiles: List<Double>,
  private val tDigest: () -> SlidingWindowDigest<*>
) : Histogram {

  val metrics: MutableMap<Long, DigestMetric> = mutableMapOf()

  /** Records a new metric within the histogram */
  override fun record(duration: Double, vararg labelValues: String) {
    if (!metrics.containsKey(key(labelValues))) {
      val metric = DigestMetric(tDigest(), labelValues.asList())
      metrics.put(key(labelValues), metric)
    }
    metrics.getValue(key(labelValues)).observe(duration)
  }

  /** Returns the number of windows within the histogram */
  override fun count(vararg labelValues: String): Int {
    if (metrics.containsKey(key(labelValues))) {
      return metrics.getValue(key(labelValues)).digest.windows.count()
    }
    return  0
  }

  /** Returns a metric of the histogram. Order of labels matters */
  fun getMetric(vararg labelValues: String): DigestMetric? {
    return metrics[key(labelValues)]
  }

  /** Returns the name of the histogram */
  fun getName(): String {
    return name
  }

  /** Returns the help of the histogram */
  fun getHelp(): String {
    return help
  }

  /** Returns the quantiles of the histogram */
  fun getQuantiles(): List<Double> {
    return quantiles
  }

  /** Generates a unique key to store each metric under */
  fun key(labels: Array<out String>): Long {
    var hash: Long = 0
    labels.forEach { key ->
      hash += key.hashCode()
    }
    return hash
  }
}

/** Default quantiles that can be used to register with a new histogram */
val defaultQuantiles = mapOf(0.5 to 0.05, 0.75 to 0.02, 0.95 to 0.01, 0.99 to 0.001, 0.999 to 0.0001)

/** TDigestHistogramRegistry registers all TDigestHistograms */
class TDigestHistogramRegistry <T : TDigest<T>> constructor(
    private val newDigestFn: () -> SlidingWindowDigest<*> =
        fun() = SlidingWindowDigest(
            Windower(windowSecs = 30, stagger = 3),
            fun() = VeneurDigest())
  ): HistogramRegistry {

  private val histograms: MutableMap<String, TDigestHistogram<T>> = mutableMapOf()

  /** Creates and returns a new histogram. Histogram is registered within the Registry */
  override fun newHistogram(
    name: String,
    help: String,
    labelNames: List<String>,
    quantiles: Map<Double, Double>
  ): TDigestHistogram<T> {
    val histogram = TDigestHistogram<T>(name, help, quantiles.keys.toList(), fun() = newDigestFn())
    return register(histogram)
  }

  /** Registers a histogram within the Registry */
  @Synchronized fun register(histogram: TDigestHistogram<T>): TDigestHistogram<T> {
    if  (!histograms.containsKey(histogram.getName())) {
      histograms[histogram.getName()] = histogram
    }
    return histograms.getValue(histogram.getName())
  }

  /** Returns a list of all Histograms registered by the Registry */
  @Synchronized fun allHistograms(): List<TDigestHistogram<*>> {
    return histograms.values.toList()
  }


  /** Returns a histogram set under the given labels */
  @Synchronized fun getHistogram(labels: String): TDigestHistogram<T>? {
    return histograms[labels]
  }
}