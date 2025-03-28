package misk.audit

/**
 * Configuration for a Misk Audit Client.
 */
data class AuditClientConfig(
  /** The base URL of the audit service. */
  val url: String,
)
