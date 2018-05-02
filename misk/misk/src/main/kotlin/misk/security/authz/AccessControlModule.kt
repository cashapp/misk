package misk.security.authz

import com.google.inject.TypeLiteral
import misk.ApplicationInterceptor
import misk.MiskCaller
import misk.inject.addMultibinderBinding
import misk.inject.newMultibinder
import misk.inject.to
import misk.scope.ActionScoped
import misk.scope.ActionScopedProvider
import misk.scope.ActionScopedProviderModule
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * Installs a binding for the [ActionScoped] [MiskCaller]?, along with support for
 * perform access control checks for actions based on the incoming caller
 */
class AccessControlModule(
  private val authenticators: List<KClass<out MiskCallerAuthenticator>>
) : ActionScopedProviderModule() {

  constructor(vararg authenticators: KClass<out MiskCallerAuthenticator>) :
      this(authenticators.toList())

  override fun configureProviders() {
    bindProvider(miskCallerType, MiskCallerProvider::class)
    binder().newMultibinder<MiskCallerAuthenticator>() // In case no authenticators are registered
    authenticators.forEach { authenticator ->
      binder().newMultibinder<MiskCallerAuthenticator>().addBinding().to(authenticator.java)
    }
    binder().addMultibinderBinding<ApplicationInterceptor.Factory>().to<AccessInterceptor.Factory>()
  }

  class MiskCallerProvider : ActionScopedProvider<MiskCaller?> {
    @Inject lateinit
    var authenticators: @JvmSuppressWildcards MutableList<out MiskCallerAuthenticator>

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