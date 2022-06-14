package misk.policy.opa

/**
 * OPA Response wrapper.
 * Every response has this standard shape, made concrete by the expected response type.
 */
data class Response<T>(
  val decision_id: String?,
  val result: T?,
  val provenance: Provenance?
)

data class Provenance(
  val version: String?,
  val build_commit: String?,
  val build_timestamp: String?,
  val build_hostname: String?,
  val revision: String?
)

interface OpaResponse {
  var provenance: Provenance?
}
