package misk.policy.opa

/**
 * OPA Request wrapper.
 */
data class Request<T>(
  val input: T
)
