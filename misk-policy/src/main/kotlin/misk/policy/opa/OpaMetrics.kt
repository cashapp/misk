package misk.policy.opa

interface OpaMetrics {

  enum class Names {
    opa_server_query_cache_hit,
    opa_rego_external_resolve,
    opa_rego_input_parse,
    opa_rego_query_eval,
    opa_server_handler,
    opa_rego_evaluated,
  }

  /** Increments a counter to indicate policy evaluation, whether metrics are enabled on the request or not. */
  fun evaluated(document: String)

  /** Dispatches [OpaResponse.metrics] into a metrics client. */
  fun observe(document: String, response: OpaResponse)
}
