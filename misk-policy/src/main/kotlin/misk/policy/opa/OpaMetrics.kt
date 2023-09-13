package misk.policy.opa

interface OpaMetrics {

  /**
   * Dispatches [OpaResponse.metrics] into a metrics client.
   */
  fun observe(document: String, response: OpaResponse)

}
