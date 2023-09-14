package misk.policy.opa

interface OpaMetrics {

  /**
   * Increments a counter to indicate policy evaluation, whether metrics are enabled on the
   * request or not.
   */
  fun evaluated(document: String)

  /**
   * Dispatches [OpaResponse.metrics] into a metrics client.
   */
  fun observe(document: String, response: OpaResponse)

}
