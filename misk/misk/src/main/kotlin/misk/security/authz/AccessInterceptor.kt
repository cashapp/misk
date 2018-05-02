package misk.security.authz

import misk.Action
import misk.ApplicationInterceptor
import misk.Chain
import misk.MiskCaller
import misk.exceptions.UnauthenticatedException
import misk.exceptions.UnauthorizedException
import misk.scope.ActionScoped
import javax.inject.Inject
import kotlin.reflect.full.findAnnotation

class AccessInterceptor private constructor(
  private val allowedServices: Set<String>,
  private val allowedRoles: Set<String>,
  private val caller: ActionScoped<MiskCaller?>
) : ApplicationInterceptor {

  override fun intercept(chain: Chain): Any {
    val caller = caller.get() ?: throw UnauthenticatedException()
    if (!isAllowed(caller)) {
      throw UnauthorizedException()
    }

    return chain.proceed(chain.args)
  }

  private fun isAllowed(caller: MiskCaller): Boolean {
    // Allow if we don't have any requirements on service or role
    if (allowedServices.isEmpty() && allowedRoles.isEmpty()) return true

    // Allow if the caller has provided an allowed service
    if (caller.service != null && allowedServices.contains(caller.service)) return true

    // Allow if the caller has provided an allowed role
    return caller.roles.any { allowedRoles.contains(it) }
  }

  class Factory @Inject internal constructor(
    private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>
  ) : ApplicationInterceptor.Factory {
    override fun create(action: Action): ApplicationInterceptor? {
      val authenticated = action.function.findAnnotation<Authenticated>()
      if (authenticated == null) {
        // One of Authenticated or Unauthenticated must be specified
        check(action.function.findAnnotation<Unauthenticated>() != null) {
          "invalid action ${action.name}: one of @Authenticated or @Unauthenticated must be provided"
        }
        return null
      }

      return AccessInterceptor(authenticated.services.toSet(), authenticated.roles.toSet(), caller)
    }
  }
}

