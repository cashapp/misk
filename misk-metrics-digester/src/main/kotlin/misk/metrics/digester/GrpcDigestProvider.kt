package misk.metrics.digester

import com.squareup.digester.protos.service.GetDigestsRequest
import com.squareup.digester.protos.service.GetDigestsResponse
import com.squareup.digester.protos.service.MetricFamily
import misk.grpc.GrpcClient
import misk.logging.getLogger
import misk.logging.log
import java.lang.StringBuilder
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

  private val logger =  getLogger<DigestProviderServer<*>>()

  val registry = TDigestHistogramRegistry.newVeneurRegistry()
  val grpcServerMethosMsMetric = registry.newHistogram(
      "demo.framework.actions.grpc.response_time_ms",
      "Duration in milleseconds taken to proccess a GRPC method call on the server",
      emptyList(),
      defaultQuantiles
  )


class DigestProviderServer<T : TDigest<T>>(req: GetDigestsRequest, srv: Any) {

  /** Streams metric digests for metrics available in the request's time range */
  fun getDigests(req: GetDigestsRequest, server: GetDigestsResponse) {
    require(req.windows_end_from_ms <= req.windows_end_to_ms) {
      "windows_end_from_ms cannot be later then window_end_to_ms"
    }

    val histograms = registry.allHistograms()
    val from = ZonedDateTime.ofInstant(Instant.ofEpochMilli(req.windows_end_from_ms), ZoneId.of("UTC"))
    val to = ZonedDateTime.ofInstant(Instant.ofEpochMilli(req.windows_end_to_ms), ZoneId.of("UTC"))


    var metricCount: Int = 0
    histograms.forEach { histogram ->
      val metricFamily = histogramToMetricFamily(histogram, from, to)
      if (metricFamily != null) {
        metricCount += metricFamily.metrics.count()
        //SEND - not completely sure how this is going to work. The golang implementation has a DigestProvider service
        // which does not seem to be generated on the kotlin side for some reason
      }
    }

    val builder = StringBuilder()
    builder.append("metric_family_count: ")
    builder.append(histograms.count())
    builder.appendln()
    builder.append("digest_count: ")
    builder.append(metricCount)
    builder.appendln()
    builder.append("sent digests")

    logger.debug { builder.toString() }
  }


  /**
   * Returns MetricFamily proto containing all windows end in the [from, to] range
   * Returns null if the histogram has no data in given range
   */
  fun histogramToMetricFamily(
    histogram: TDigestHistogram<VeneurDigest>,
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
          1, //1 stagger set as place holder
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