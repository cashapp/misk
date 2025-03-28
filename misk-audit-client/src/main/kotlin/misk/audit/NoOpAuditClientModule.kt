package misk.audit

import misk.inject.KAbstractModule
import misk.inject.asSingleton

/**
 * A no-op implementation of [AuditClient] that does nothing.
 *
 * This is useful for testing or when you don't want to send audit events to a remote service.
 */
class NoOpAuditClientModule: KAbstractModule() {
  override fun configure() {
    bind<AuditClient>().to<NoOpAuditClient>().asSingleton()
  }
}
