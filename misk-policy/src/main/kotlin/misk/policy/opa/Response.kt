package misk.policy.opa

/** OPA Response wrapper. Every response has this standard shape, made concrete by the expected response type. */
data class Response<T>(val decision_id: String?, val result: T?, val provenance: Provenance?, val metrics: Metrics?)

data class Provenance(
  val version: String?,
  val build_commit: String?,
  val build_timestamp: String?,
  val build_hostname: String?,
  val revision: String?,
  val bundles: Map<String, ProvenanceBundle>?,
)

data class ProvenanceBundle(val revision: String?)

data class Metrics(
  val counter_server_query_cache_hit: Long,
  val timer_rego_external_resolve_ns: Long,
  val timer_rego_input_parse_ns: Long,
  val timer_rego_query_eval_ns: Long,
  val timer_server_handler_ns: Long,
)

abstract class OpaResponse {
  var provenance: Provenance? = null
  var metrics: Metrics? = null
}
