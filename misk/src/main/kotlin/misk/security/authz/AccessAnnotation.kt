package misk.security.authz

import kotlin.reflect.KClass

/**
 * Use this to alias an annotation to a set of services and capabilities. This can be used to decouple code
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
 * class DiscoverDinosaurAction @Inject constructor() : WebAction {
 *   @Get("/discover")
 *   @PaleontologistAccess
 *   fun discover()
 * }
 *
 * class DigUpDinosaurAction @Inject constructor() : WebAction {
 *   @Get("/dig")
 *   @PaleontologistAccess
 *   fun dig()
 * }
 * ```
 *
 * Finally we use multibindings to specify which services and capabilities are permitted:
 *
 * ```
 * multibind<AccessAnnotationEntry>().toInstance(
 *  AccessAnnotationEntry<PaleontologistAccess>(capabilities = listOf("paleontologist", "intern")))
 * ```
 */
data class AccessAnnotationEntry(
  val annotation: KClass<out Annotation>,
  val services: List<String> = listOf(),
  val capabilities: List<String> = listOf()
)

inline fun <reified T : Annotation> AccessAnnotationEntry(
  services: List<String> = listOf(),
  capabilities: List<String> = listOf()
): AccessAnnotationEntry {
  return AccessAnnotationEntry(T::class, services, capabilities)
}
