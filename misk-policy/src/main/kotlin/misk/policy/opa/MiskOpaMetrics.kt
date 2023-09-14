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
  internal enum class MetricType {
    opa_server_query_cache_hit,
    opa_rego_external_resolve,
    opa_rego_input_parse,
    opa_rego_query_eval,
    opa_server_handler,
    opa_rego_evaluated
  }

  companion object {
    const val NANOSECONDS_PER_SECOND: Double = 1E9
  }

  private val serverQueryCacheHit: Counter = metrics.counter(
    MetricType.opa_server_query_cache_hit.name,
    "Number of cache hits for a successful query.",
    listOf("document")
  )

  private val regoExternalResolve: Histogram = metrics.histogram(
    MetricType.opa_rego_external_resolve.name,
    "Time taken (in seconds) to resolve external data on a successful query.",
    listOf("document")
  )

  private val regoInputParse: Histogram = metrics.histogram(
    MetricType.opa_rego_input_parse.name,
    "Time taken (in seconds) to parse the input for a successful query.",
    listOf("document")
  )

  private val regoQueryEval: Histogram = metrics.histogram(
    MetricType.opa_rego_query_eval.name,
    "Time taken (in seconds) to evaluate a successful query.",
    listOf("document")
  )

  private val serverHandler: Histogram = metrics.histogram(
    MetricType.opa_server_handler.name,
    "Time take (in seconds) to handle a successful API request.",
    listOf("document")
  )

  private val opaRegoEvaluated: Counter = metrics.counter(
    MetricType.opa_rego_evaluated.name,
    "Count of evaluations on a policy, whether it was successful or not.",
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
    regoExternalResolve.labels(document)
      .observe(_metrics.timer_rego_external_resolve_ns / NANOSECONDS_PER_SECOND)
    regoInputParse.labels(document)
      .observe(_metrics.timer_rego_input_parse_ns / NANOSECONDS_PER_SECOND)
    regoQueryEval.labels(document)
      .observe(_metrics.timer_rego_query_eval_ns / NANOSECONDS_PER_SECOND)
    serverHandler.labels(document)
      .observe(_metrics.timer_server_handler_ns / NANOSECONDS_PER_SECOND)

  }

}
