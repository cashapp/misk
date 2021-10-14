package misk.security.authz

import misk.Action
import misk.ApplicationInterceptor
import misk.Chain
import misk.MiskCaller
import misk.exceptions.UnauthenticatedException
import misk.exceptions.UnauthorizedException
import misk.scope.ActionScoped
import wisp.logging.getLogger
import javax.inject.Inject
import kotlin.reflect.KClass

class AccessInterceptor private constructor(
  val allowedServices: Set<String>,
  val allowedCapabilities: Set<String>,
  private val caller: ActionScoped<MiskCaller?>
) : ApplicationInterceptor {

  override fun intercept(chain: Chain): Any {
    val caller = caller.get() ?: throw UnauthenticatedException()
    if (!caller.isAllowed(allowedCapabilities, allowedServices)) {
      logger.warn { "$caller is not allowed to access ${chain.action}" }
      throw UnauthorizedException()
    }

    return chain.proceed(chain.args)
  }

  internal class Factory @Inject internal constructor(
    private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
    private val registeredEntries: List<AccessAnnotationEntry>
  ) : ApplicationInterceptor.Factory {
    override fun create(action: Action): ApplicationInterceptor? {
      // Gather all of the access annotations on this action.
      val actionEntries = mutableListOf<AccessAnnotationEntry>()
      for (annotation in action.function.annotations) when {
        annotation is Authenticated -> actionEntries += annotation.toAccessAnnotationEntry()
        annotation is Unauthenticated -> actionEntries += annotation.toAccessAnnotationEntry()
        registeredEntries.find { it.annotation == annotation.annotationClass } != null -> {
          actionEntries += registeredEntries.find { it.annotation == annotation.annotationClass }!!
        }
      }

      // No access annotations. Fail with a useful message.
      check(actionEntries.isNotEmpty()) {
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
          """.trimMargin()
      }

      // This action is explicitly marked as unauthenticated.
      if (action.hasAnnotation<Unauthenticated>()) {
        check(actionEntries.size == 1) {
          val otherAnnotations = actionEntries
            .filterNot { it.annotation == Unauthenticated::class }
            .map { "@${it.annotation.qualifiedName!!}" }
            .sorted()
            .joinToString()
          """${action.name}::${action.function.name}() is annotated with @${Unauthenticated::class.qualifiedName}, but also annotated with the following access annotations: $otherAnnotations. This is a contradiction.
          """.trimIndent()
        }
        return null
      }

      // Return an interceptor representing the union of the capabilities/services of all
      // annotations.
      return AccessInterceptor(
        actionEntries.flatMap { it.services }.toSet(),
        actionEntries.flatMap { it.capabilities }.toSet(),
        caller
      )
    }

    private fun Authenticated.toAccessAnnotationEntry() = AccessAnnotationEntry(
      Authenticated::class, services.toList(), capabilities.toList()
    )

    private fun Unauthenticated.toAccessAnnotationEntry() = AccessAnnotationEntry(
      Unauthenticated::class, listOf(), listOf()
    )

    private inline fun <reified T : Annotation> Action.hasAnnotation() =
      function.annotations.any { it.annotationClass == T::class }
  }

  companion object {
    val logger = getLogger<AccessInterceptor>()
  }
}
