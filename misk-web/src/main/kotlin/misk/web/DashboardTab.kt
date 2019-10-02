package misk.web

import misk.security.authz.AccessAnnotationEntry
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KClass

class DashboardTab(
  slug: String,
  url_path_prefix: String,
  val name: String,
  val category: String = "Admin",
  capabilities: Set<String> = setOf(),
  services: Set<String> = setOf()
) : WebTab(slug, url_path_prefix, capabilities, services)

/**
 * Sets the tab's authentication based on the injected AdminDashboardAccess access annotation entry
 */
class DashboardTabProvider(
  val slug: String,
  val url_path_prefix: String,
  val name: String,
  val category: String = "Admin",
  val accessAnnotation: KClass<out Annotation>
) : Provider<DashboardTab> {
  @Inject
  lateinit var registeredEntries: List<AccessAnnotationEntry>

  override fun get(): DashboardTab {
    val accessAnnotationEntry = registeredEntries.find { it.annotation == accessAnnotation }!!
    return DashboardTab(
      slug = slug,
      url_path_prefix = url_path_prefix,
      name = name,
      category = category,
      capabilities = accessAnnotationEntry.capabilities.toSet(),
      services = accessAnnotationEntry.services.toSet()
    )
  }
}

inline fun <reified A : Annotation> DashboardTabProvider(
  slug: String,
  url_path_prefix: String,
  name: String,
  category: String = "Admin"
) = DashboardTabProvider(
  slug = slug,
  url_path_prefix = url_path_prefix,
  name = name,
  category = category,
  accessAnnotation = A::class
)