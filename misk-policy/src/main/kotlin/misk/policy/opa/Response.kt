package misk.policy.opa

/**
 * OPA Response wrapper.
 * Every response has this standard shape, made concrete by the expected response type.
 */
data class Response<T>(
  val decision_id: String?,
  val result: T?
)
