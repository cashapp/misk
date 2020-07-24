package misk.security.authz

import misk.ApplicationInterceptor
import misk.scope.ActionScopedProviderModule

/**
 * Install support for performing access control checks for actions based on the incoming caller.
 */
class AccessControlModule : ActionScopedProviderModule() {
  override fun configureProviders() {
    multibind<ApplicationInterceptor.Factory>().to<AccessInterceptor.Factory>()
    newMultibinder<AccessAnnotationEntry>()
  }
}
