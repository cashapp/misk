package misk.security.authz

import com.google.inject.BindingAnnotation
import kotlin.reflect.KClass

/**
 * Use this to alias an annotation to a set of services and capabilities. This can be used to decouple code that needs
 * access control from the policy that defines it.
 *
 * To demonstrate, let's define an access annotation. By convention these annotations are suffixed `Access`:
 * ```
 * @Retention(AnnotationRetention.RUNTIME)
 * @Target(AnnotationTarget.FUNCTION)
 * annotation class PaleontologistAccess
 * ```
 *
 * Next we define actions that apply our annotation:
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
 * ```
 * multibind<AccessAnnotationEntry>().toInstance(
 *  AccessAnnotationEntry<PaleontologistAccess>(capabilities = listOf("paleontologist", "intern")))
 * ```
 */
data class AccessAnnotationEntry
@JvmOverloads
constructor(
  val annotation: KClass<out Annotation>,
  val services: List<String> = listOf(),
  val capabilities: List<String> = listOf(),
  val allowAnyService: Boolean = false,
  val allowAnyUser: Boolean = false,
)

inline fun <reified T : Annotation> AccessAnnotationEntry(
  services: List<String> = listOf(),
  capabilities: List<String> = listOf(),
  allowAnyService: Boolean = false,
  allowAnyUser: Boolean = false,
): AccessAnnotationEntry {
  return AccessAnnotationEntry(T::class, services, capabilities, allowAnyService, allowAnyUser)
}

/**
 * Exclude a service from @AllowAnyService.
 *
 * Add any external proxies that do service-to-service authentication to prevent AllowAnyService from also allowing
 * external traffic to your service.
 *
 * You can still explicitly include these services by including them in
 *
 * @Authenticated(services=["my-proxy"]) or with an equivalent decorator that creates an AccessAnnotationEntry.
 *
 * Usage: multibind<String, ExcludeFromAllowAnyService>().toInstance("web-proxy") multibind<String,
 * ExcludeFromAllowAnyService>().toInstance("access-proxy")
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@BindingAnnotation
annotation class ExcludeFromAllowAnyService
