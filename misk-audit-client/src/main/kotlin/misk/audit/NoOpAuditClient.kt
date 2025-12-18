package misk.audit

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Instant

/**
 * No-op implementation of [AuditClient] that does nothing when logEvent is called.
 *
 * This is useful for testing or when you don't want to send audit events to a remote service.
 */
@Singleton
class NoOpAuditClient @Inject constructor() : AuditClient {
  override fun logEvent(
    target: String,
    description: String,
    automatedChange: Boolean,
    richDescription: String?,
    detailURL: String?,
    approverLDAP: String?,
    requestorLDAP: String?,
    applicationName: String?,
    environment: String?,
    timestampSent: Instant?,
  ) {
    Unit
  }
}
