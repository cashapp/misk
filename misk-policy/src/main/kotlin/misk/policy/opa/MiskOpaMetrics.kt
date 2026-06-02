package misk.policy.opa

import io.prometheus.client.Counter
import io.prometheus.client.Histogram
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.lang.IllegalArgumentException
import misk.metrics.v2.Metrics

/** Maps [OpaResponse.metrics] into prometheus counters and histograms. */
@Singleton
class MiskOpaMetrics @Inject constructor(metrics: Metrics) : OpaMetrics {

  @Suppress("ktlint:enum-entry-name-case")
  @Deprecated("Use OpaMetrics.Names instead", ReplaceWith("OpaMetrics.Names", "misk.policy.opa.OpaMetrics"))
  internal enum class MetricType {
    opa_server_query_cache_hit,
    opa_rego_external_resolve,
    opa_rego_input_parse,
    opa_rego_query_eval,
    opa_server_handler,
    opa_rego_evaluated,
  }

  private val serverQueryCacheHit: Counter =
    metrics.counter(
      OpaMetrics.Names.opa_server_query_cache_hit.name,
      "Number of cache hits for a successful query.",
      listOf("document"),
    )

  private val regoExternalResolve: Histogram =
    metrics.histogram(
      OpaMetrics.Names.opa_rego_external_resolve.name,
      "Time taken to resolve external data on a successful query.",
      listOf("document"),
    )

  private val regoInputParse: Histogram =
    metrics.histogram(
      OpaMetrics.Names.opa_rego_input_parse.name,
      "Time taken to parse the input for a successful query.",
      listOf("document"),
    )

  private val regoQueryEval: Histogram =
    metrics.histogram(
      OpaMetrics.Names.opa_rego_query_eval.name,
      "Time taken to evaluate a successful query.",
      listOf("document"),
    )

  private val serverHandler: Histogram =
    metrics.histogram(
      OpaMetrics.Names.opa_server_handler.name,
      "Time take to handle a successful API request.",
      listOf("document"),
    )

  private val opaRegoEvaluated: Counter =
    metrics.counter(
      OpaMetrics.Names.opa_rego_evaluated.name,
      "Count of evaluations on a policy, whether it was successful or not.",
      listOf("document"),
    )

  override fun evaluated(document: String) {
    opaRegoEvaluated.labels(document).inc()
  }

  /** Dispatches [OpaResponse.metrics] into the prometheus client. */
  override fun observe(document: String, response: OpaResponse) {

    val _metrics = response.metrics ?: throw IllegalArgumentException("No metrics found on response")

    serverQueryCacheHit.labels(document).inc(_metrics.counter_server_query_cache_hit.toDouble())
    regoExternalResolve.labels(document).observe(_metrics.timer_rego_external_resolve_ns.toDouble())
    regoInputParse.labels(document).observe(_metrics.timer_rego_input_parse_ns.toDouble())
    regoQueryEval.labels(document).observe(_metrics.timer_rego_query_eval_ns.toDouble())
    serverHandler.labels(document).observe(_metrics.timer_server_handler_ns.toDouble())
  }
}
