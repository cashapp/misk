package misk.security.authz

import kotlin.reflect.KClass

/**
 * Use this to alias an annotation to a set of services and roles. This can be used to decouple code
 * that needs access control from the policy that defines it.
 *
 * To demonstrate, let's define an access annotation. By convention these annotations are suffixed
 * `Access`:
 *
 * ```
 * @Retention(AnnotationRetention.RUNTIME)
 * @Target(AnnotationTarget.FUNCTION)
 * annotation class PaleontologistAccess
 * ```
 *
 * Next we define actions that apply our annotation:
 *
 * ```
 * class DiscoverDinosaurAction : WebAction {
 *   @Get("/discover")
 *   @PaleontologistAccess
 *   fun discover()
 * }
 *
 * class DigUpDinosaurAction : WebAction {
 *   @Get("/dig")
 *   @PaleontologistAccess
 *   fun dig()
 * }
 * ```
 *
 * Finally we use multibindings to specify which services and roles are permitted:
 *
 * ```
 * multibind<AccessAnnotation>().toInstance(
 *  AccessAnnotation<PaleontologistAccess>(roles = listOf("paleontologist", "intern")))
 * ```
 */
data class AccessAnnotation(
  val annotation: KClass<out Annotation>,
  val services: List<String> = listOf(),
  val roles: List<String> = listOf()
)

inline fun <reified T : Annotation> AccessAnnotation(
  services: List<String> = listOf(),
  roles: List<String> = listOf()
): AccessAnnotation {
  return AccessAnnotation(T::class, services, roles)
}
