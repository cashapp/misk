package misk.security.authz

import com.google.inject.TypeLiteral
import misk.ApplicationInterceptor
import misk.MiskCaller
import misk.scope.ActionScoped
import misk.scope.ActionScopedProvider
import misk.scope.ActionScopedProviderModule
import javax.inject.Inject

/**
 * Installs a binding for the [ActionScoped] [MiskCaller]?, along with support for performing access
 * control checks for actions based on the incoming caller.
 */
class AccessControlModule : ActionScopedProviderModule() {
  override fun configureProviders() {
    bindProvider(miskCallerType, MiskCallerProvider::class)
    multibind<ApplicationInterceptor.Factory>().to<AccessInterceptor.Factory>()

    // Initialize empty sets for our multibindings.
    newMultibinder<MiskCallerAuthenticator>()
    newMultibinder<AccessAnnotation>()
  }

  class MiskCallerProvider : ActionScopedProvider<MiskCaller?> {
    @Inject lateinit var authenticators: List<MiskCallerAuthenticator>

    override fun get(): MiskCaller? {
      return authenticators.mapNotNull {
        it.getAuthenticatedCaller()
      }.firstOrNull()
    }
  }

  private companion object {
    val miskCallerType = object : TypeLiteral<MiskCaller?>() {}
  }
}