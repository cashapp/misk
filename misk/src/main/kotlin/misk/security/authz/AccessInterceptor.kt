package misk.security.authz

import misk.Action
import misk.ApplicationInterceptor
import misk.Chain
import misk.MiskCaller
import misk.exceptions.UnauthenticatedException
import misk.exceptions.UnauthorizedException
import misk.scope.ActionScoped
import misk.web.metadata.AdminDashboardAccess
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

internal class AccessInterceptor private constructor(
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

  internal class Factory @Inject internal constructor(
    private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") private val accessAnnotations: java.util.List<out AccessAnnotation>
  ) : ApplicationInterceptor.Factory {
    override fun create(action: Action): ApplicationInterceptor? {
      // Gather all of the access annotations on this action.
      val actionAnnotations = mutableListOf<AccessAnnotation>()
      val authenticated = action.function.findAnnotation<Authenticated>()
      if (authenticated != null) {
        actionAnnotations += authenticated.toAccessAnnotation()
      }
      actionAnnotations += accessAnnotations.filter { action.hasAnnotation(it.annotation) }

      // This action is explicitly marked @Authenticated or with a custom annotation.
      if (actionAnnotations.size == 1) {
        return AccessInterceptor(actionAnnotations[0].services.toSet(),
            actionAnnotations[0].roles.toSet(), caller)
      }

      // This action is explicitly marked as unauthenticated.
      if (actionAnnotations.isEmpty() && action.hasAnnotation(Unauthenticated::class)) {
        return null
      }

      // Not exactly one access annotation. Fail with a useful message.
      val requiredAnnotations = mutableListOf<KClass<out Annotation>>()
      requiredAnnotations += Authenticated::class
      requiredAnnotations += Unauthenticated::class
      requiredAnnotations += AdminDashboardAccess::class
      requiredAnnotations += accessAnnotations.map { it.annotation }
      throw IllegalStateException(
          "action ${action.name} must have one of the following annotations: $requiredAnnotations")
    }

    private fun Authenticated.toAccessAnnotation() = AccessAnnotation(
        Authenticated::class, services.toList(), roles.toList())

    private fun Action.hasAnnotation(annotationClass: KClass<out Annotation>) =
        function.annotations.any { it.annotationClass == annotationClass }
  }
}

