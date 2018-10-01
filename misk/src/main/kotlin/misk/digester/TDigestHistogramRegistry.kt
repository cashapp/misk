package misk.digester

import io.prometheus.client.CollectorRegistry
import misk.metrics.HistogramRegistry
import javax.inject.Inject

class TDigestHistogramRegistry: HistogramRegistry {

  //@Inject lateinit var collectorRegistry: CollectorRegistry

  private val collectorRegistry = HashMap<String, TDigestHistogram>()

  override fun newHistogram(
    name: String,
    help: String,
    labelNames: List<String>,
    buckets: DoubleArray?
  ): TDigestHistogram {

    if (collectorRegistry.containsKey(name)) {
      throw IllegalArgumentException("Collector already registered that provides name: $name")
    }

    var tDigestHistogramObject = TDigestHistogram(name, help, labelNames, buckets)
    collectorRegistry.put(name, tDigestHistogramObject)
    return tDigestHistogramObject
  }

  fun getTDigests(): List<TDigestHistogram> {
    return collectorRegistry.values.toList()
  }

  fun getTDigest(name: String): TDigestHistogram? {
    return collectorRegistry[name]
  }

}