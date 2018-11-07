package misk.metrics.digester

import com.squareup.digester.protos.service.GetDigestsRequest
import com.squareup.digester.protos.service.MetricFamily
import java.time.Instant
import java.time.ZonedDateTime


  val registry = TDigestHistogramRegistry.newVeneurRegistry()
  val grpcServerMethosMsMetric = registry.newHistogram(
      "demo.framework.actions.grpc.response_time_ms",
      "Duration in milleseconds taken to proccess a GRPC method call on the server",
      emptyList(),
      defaultQuantiles
  )

class DigestProviderService<T : TDigest<T>> {

  /** Streams metric digests for metrics available in the request's time range */
  fun getDigests(digestsRequest: GetDigestsRequest, server: DigestMetric<>) {
    val histograms = registry.allHistograms()

    val logger =
  }


  /**
   * Returns MetricFamily proto containing all windows end in the [from, to] range
   * Returns null if the histogram has no data in given range
   */
  fun histogramToMetricFamily(
    histogram: TDigestHistogram<T>,
    from: ZonedDateTime,
    to: ZonedDateTime
  ): MetricFamily? {
    val digestMetrics = histogram.allMetrics()

    val metrics = mutableListOf<MetricFamily.Metric>()
    digestMetrics.forEach { digestMetric ->
      val metric = digestMetricToProto(digestMetric, from, to)
      if (metric != null) {
        metrics.add(metric)
      }
    }

    if (metrics.count() == 0) {
      return null
    }

    return MetricFamily(
        MetricFamily.MetricDescriptor(histogram.name, histogram.help),
        metrics
    )
  }

  /**
   * Returns a metric proto containing all windows within the given digestMetric ending in the [from, to] range
   * If the metric has no data in the time range then null is returned
   */
  fun digestMetricToProto(
    digestMetric: DigestMetric,
    from: ZonedDateTime,
    to: ZonedDateTime
  ): MetricFamily.Metric? {
    val digests = digestsInRange(digestMetric, from, to)
    if (digests.size == 0) return null

    val digestProtos: MutableList<MetricFamily.Digest> = mutableListOf()
    digests.forEachIndexed { i, digest ->
      digestProtos.add(MetricFamily.Digest(
          Instant.from(digest.window.start).toEpochMilli(),
          Instant.from(digest.window.end).toEpochMilli(),
          0, //0 stagger set as place holder
          digest.digest.proto()
      ))
    }

    val labelMap = mutableMapOf<String, String>()
    digestMetric.labels.forEach { label ->
      labelMap.putIfAbsent(label, "")
    }

    return MetricFamily.Metric(
        labelMap,
        digestProtos
    )

  }

  /** Returns all WindowDigests within the [from, to] range */
  fun digestsInRange(
    metrics: DigestMetric,
    from: ZonedDateTime,
    to: ZonedDateTime
  ): List<WindowDigest<out TDigest<*>>> {
    val digests = metrics.digest.closedDigests(from)
    if (digests.count() == 0) return emptyList()

    digests.forEachIndexed { i, digest ->
      if (digest.window.end.isBefore(from) || to.isBefore(digest.window.end)) {
        return digests.subList(0, i)
      }
    }
    return digests
  }
}