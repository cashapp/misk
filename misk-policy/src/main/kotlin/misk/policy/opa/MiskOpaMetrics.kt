package misk.policy.opa

import io.prometheus.client.Counter
import io.prometheus.client.Histogram
import jakarta.inject.Inject
import misk.metrics.v2.Metrics
import java.lang.IllegalArgumentException

/**
 * Maps [OpaResponse.metrics] into prometheus counters and histograms.
 */
class MiskOpaMetrics @Inject constructor(metrics: Metrics) : OpaMetrics {

  @Suppress("ktlint:enum-entry-name-case")
  enum class MetricType {
    opa_server_query_cache_hit,
    opa_rego_external_resolve_ns,
    opa_rego_input_parse_ns,
    opa_rego_query_eval_ns,
    opa_server_handler_ns,
    opa_rego_evaluated
  }

  companion object {
    const val NANOSECONDS_PER_SECOND = 1E9
  }

  private val serverQueryCacheHit: Counter = metrics.counter(
    MetricType.opa_server_query_cache_hit.name,
    "Number of cache hits for the query.",
    listOf("document")
  )

  private val regoExternalResolveNs: Histogram = metrics.histogram(
    MetricType.opa_rego_external_resolve_ns.name,
    "Time taken (in nanoseconds) to resolve external data.",
    listOf("document")
  )

  private val regoInputParseNs: Histogram = metrics.histogram(
    MetricType.opa_rego_input_parse_ns.name,
    "Time taken (in nanoseconds) to parse the input.",
    listOf("document")
  )

  private val regoQueryEvalNs: Histogram = metrics.histogram(
    MetricType.opa_rego_query_eval_ns.name,
    "Time taken (in nanonseconds) to evaluate the query.",
    listOf("document")
  )

  private val serverHandlerNs: Histogram = metrics.histogram(
    MetricType.opa_server_handler_ns.name,
    "Time take (in nanoseconds) to handle the API request.",
    listOf("document")
  )

  private val opaRegoEvaluated: Counter = metrics.counter(
    MetricType.opa_rego_evaluated.name,
    "Count of evaluations on a policy.",
    listOf("document")
  )

  override fun evaluated(document: String) {
    opaRegoEvaluated.labels(document).inc()
  }

  /**
   * Dispatches [OpaResponse.metrics] into the prometheus client.
   */
  override fun observe(document: String, response: OpaResponse) {

    val _metrics =
      response.metrics ?: throw IllegalArgumentException("No metrics found on response")

    serverQueryCacheHit.labels(document).inc(_metrics.counter_server_query_cache_hit.toDouble())
    regoExternalResolveNs.labels(document)
      .observe(_metrics.timer_rego_external_resolve_ns / NANOSECONDS_PER_SECOND)
    regoInputParseNs.labels(document)
      .observe(_metrics.timer_rego_input_parse_ns / NANOSECONDS_PER_SECOND)
    regoQueryEvalNs.labels(document)
      .observe(_metrics.timer_rego_query_eval_ns / NANOSECONDS_PER_SECOND)
    serverHandlerNs.labels(document)
      .observe(_metrics.timer_server_handler_ns / NANOSECONDS_PER_SECOND)

  }

}
