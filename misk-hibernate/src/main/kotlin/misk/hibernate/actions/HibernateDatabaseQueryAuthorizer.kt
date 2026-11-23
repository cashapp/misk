package misk.hibernate.actions

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.MiskCaller
import misk.security.authz.AccessAnnotationEntry
import misk.security.authz.ExcludeFromAllowAnyService

/**
 * Authorizes Database Query requests against the entity's [AccessAnnotationEntry] with the same deny-by-default rules
 * as [misk.security.authz.AccessInterceptor]: a missing entry, or one that grants nothing, denies every caller.
 */
@Singleton
internal class HibernateDatabaseQueryAuthorizer
@Inject
constructor(@ExcludeFromAllowAnyService private val excludeFromAllowAnyService: List<String>) {
  fun isAuthorized(caller: MiskCaller, entry: AccessAnnotationEntry?): Boolean {
    if (caller.allowAll) return true
    if (entry == null) return false

    if (entry.allowAnyService && caller.service != null && caller.service !in excludeFromAllowAnyService) return true
    if (entry.allowAnyUser && caller.user != null) return true

    return caller.hasCapability(entry.capabilities.toSet()) || caller.isService(entry.services.toSet())
  }
}
