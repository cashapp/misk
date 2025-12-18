package misk.security.authz

import jakarta.inject.Inject
import kotlin.reflect.KClass
import misk.Action
import misk.ApplicationInterceptor
import misk.Chain
import misk.MiskCaller
import misk.exceptions.UnauthenticatedException
import misk.exceptions.UnauthorizedException
import misk.logging.getLogger
import misk.scope.ActionScoped

class AccessInterceptor
private constructor(
  val allowedServices: Set<String>,
  val allowedCapabilities: Set<String>,
  private val caller: ActionScoped<MiskCaller?>,
  private val allowAnyService: Boolean,
  private val excludeFromAllowAnyService: Set<String>,
  private val allowAnyUser: Boolean,
) : ApplicationInterceptor {
  override fun intercept(chain: Chain): Any {
    val caller = caller.get() ?: throw UnauthenticatedException()
    if (!isAuthorized(caller)) {
      logger.warn { "$caller is not allowed to access ${chain.action}" }
      throw UnauthorizedException()
    }

    return chain.proceed(chain.args)
  }

  /** Check whether the caller is allowed to access this endpoint */
  private fun isAuthorized(caller: MiskCaller): Boolean {
    // Deny if we don't have any requirements on service or capability
    if (allowedServices.isEmpty() && allowedCapabilities.isEmpty() && !allowAnyService && !allowAnyUser) return false

    if (allowAnyService && caller.service != null && !excludeFromAllowAnyService.contains(caller.service)) {
      return true
    }

    if (allowAnyUser && caller.user != null) {
      return true
    }

    // Allow if the caller has provided an allowed capability
    return caller.hasCapability(allowedCapabilities) || caller.isService(allowedServices)
  }

  internal class Factory
  @Inject
  internal constructor(
    private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
    private val registeredEntries: List<AccessAnnotationEntry>,
    @ExcludeFromAllowAnyService private val excludeFromAllowAnyService: List<String>,
  ) : ApplicationInterceptor.Factory {
    override fun create(action: Action): ApplicationInterceptor? {
      // Gather all of the access annotations on this action.
      val actionEntries = mutableListOf<AccessAnnotationEntry>()
      for (annotation in action.function.annotations) when {
        annotation is Authenticated -> actionEntries += annotation.toAccessAnnotationEntry()
        registeredEntries.find { it.annotation == annotation.annotationClass } != null -> {
          actionEntries += registeredEntries.find { it.annotation == annotation.annotationClass }!!
        }
      }

      // Do not intercept if this action is explicitly marked as unauthenticated.
      if (action.hasAnnotation<Unauthenticated>()) {
        check(actionEntries.isEmpty()) {
          val otherAnnotations =
            actionEntries
              .filterNot { it.annotation == Unauthenticated::class }
              .map { "@${it.annotation.qualifiedName!!}" }
              .sorted()
              .joinToString()
          """${action.name}::${action.function.name}() is annotated with @${Unauthenticated::class.qualifiedName}, but also annotated with the following access annotations: $otherAnnotations. This is a contradiction.
          """
            .trimIndent()
        }
        return null
      }

      val allowAnyService = actionEntries.any { it.allowAnyService }
      val allowAnyUser = actionEntries.any { it.allowAnyUser }

      // No access annotations. Fail with a useful message.
      check(allowAnyService || allowAnyUser || actionEntries.isNotEmpty()) {
        val requiredAnnotations = mutableListOf<KClass<out Annotation>>()
        requiredAnnotations += Authenticated::class
        requiredAnnotations += Unauthenticated::class
        requiredAnnotations += registeredEntries.map { it.annotation }
        """You need to register an AccessAnnotationEntry to tell the authorization system which capabilities and services are allowed to access ${action.name}::${action.function.name}(). You can either:
          |
          |A) Add an AccessAnnotationEntry multibinding in a module for one of the annotations on ${action.name}::${action.function.name}():
          |   ${action.function.annotations}
          |
          |   AccessAnnotationEntry Example Multibinding:
          |   multibind<AccessAnnotationEntry>().toInstance(
          |     AccessAnnotationEntry<${
              action.function.annotations.filter {
                it.annotationClass.simpleName.toString().endsWith("Access")
              }
                .firstOrNull()?.annotationClass?.simpleName ?: "{Access Annotation Class Simple Name}"
            }>(capabilities = ???, services = ???))
          |
          |B) Add an AccessAnnotation to ${action.name}::${action.function.name}() that already has a matching AccessAnnotationEntry such as:
          |   $requiredAnnotations
          |
          |
          """
          .trimMargin()
      }

      if (actionEntries.size > 1) {
        val (openAuthEntries, closedAuthEntries) =
          actionEntries.partition { it.services.isEmpty() && it.capabilities.isEmpty() }
        if (openAuthEntries.isNotEmpty() && closedAuthEntries.isNotEmpty()) {
          val openAuthString =
            openAuthEntries.joinToString(separator = ",") { "@${it.annotation.simpleName.toString()}" }
          val closedAuthString =
            closedAuthEntries.joinToString(separator = ",") { "@${it.annotation.simpleName.toString()}" }
          logger.warn(
            "Conflicting auth annotations on ${action.name}::${action.function.name}(), $openAuthString won't have any effect due to $closedAuthString"
          )
        }
      }

      // Return an interceptor representing the union of the capabilities/services of all
      // annotations.
      val allowedServices = actionEntries.flatMap { it.services }.toSet()
      val allowedCapabilities = actionEntries.flatMap { it.capabilities }.toSet()

      if (!allowAnyService && allowedServices.isEmpty() && !allowAnyUser && allowedCapabilities.isEmpty()) {
        logger.warn {
          "${action.name}::${action.function.name}() has an empty set of allowed services and capabilities. Access will be denied. This method of allowing all services and users has been removed, use explicit boolean parameters allowAnyService or allowAnyUser instead."
        }
      }

      return AccessInterceptor(
        allowedServices,
        allowedCapabilities,
        caller,
        allowAnyService,
        excludeFromAllowAnyService.toSet(),
        allowAnyUser,
      )
    }

    private fun Authenticated.toAccessAnnotationEntry() =
      AccessAnnotationEntry(
        annotation = Authenticated::class,
        services = services.toList(),
        capabilities = capabilities.toList(),
        allowAnyService = allowAnyService,
        allowAnyUser = allowAnyUser,
      )

    private inline fun <reified T : Annotation> Action.hasAnnotation() =
      function.annotations.any { it.annotationClass == T::class }
  }

  companion object {
    private val logger = getLogger<AccessInterceptor>()
  }
}
