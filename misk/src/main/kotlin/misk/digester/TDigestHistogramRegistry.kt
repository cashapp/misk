package misk.digester

import misk.metrics.HistogramRegistry

class TDigestHistogramRegistry: HistogramRegistry {

  //@Inject lateinit var collectorRegistry: CollectorRegistry

  private val collectorRegistry = HashMap<String, VeneurDigest>()

  override fun newHistogram(
    name: String,
    help: String,
    labelNames: List<String>,
    buckets: DoubleArray?
  ): VeneurDigest {

    if (collectorRegistry.containsKey(name)) {
      throw IllegalArgumentException("Collector already registered that provides name: $name")
    }

    var tDigestHistogramObject = VeneurDigest(name, help, labelNames, buckets)
    collectorRegistry.put(name, tDigestHistogramObject)
    return tDigestHistogramObject
  }

  fun getTDigests(): List<VeneurDigest> {
    return collectorRegistry.values.toList()
  }

  fun getTDigest(name: String): VeneurDigest? {
    return collectorRegistry[name]
  }

}