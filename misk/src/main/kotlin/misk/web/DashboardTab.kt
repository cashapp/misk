package misk.web

import misk.security.authz.AccessAnnotationEntry
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KClass

class DashboardTab(
  slug: String,
  url_path_prefix: String,
  val dashboard: String,
  val name: String,
  val category: String = "Admin",
  capabilities: Set<String> = setOf(),
  services: Set<String> = setOf()
) : WebTab(slug, url_path_prefix, capabilities, services)

inline fun <reified DA : Annotation> DashboardTab(
  slug: String,
  url_path_prefix: String,
  name: String,
  category: String = "Admin",
  capabilities: Set<String> = setOf(),
  services: Set<String> = setOf()
) = DashboardTab(
  slug = slug,
  url_path_prefix = url_path_prefix,
  dashboard = DA::class.simpleName!!,
  name = name,
  category = category,
  capabilities = capabilities,
  services = services
)

/**
 * Sets the tab's authentication based on the injected AdminDashboardAccess access annotation entry
 */
class DashboardTabProviderBuilder(
  val slug: String,
  val url_path_prefix: String,
  val name: String,
  val category: String = "Admin",
  val dashboardAnnotation: KClass<out Annotation>,
  val accessAnnotation: KClass<out Annotation>? = null,
  val capabilities: Set<String> = setOf(),
  val services: Set<String> = setOf()
) : Provider<DashboardTab> {
  @Inject
  lateinit var registeredEntries: List<AccessAnnotationEntry>

  override fun get(): DashboardTab {
    val accessAnnotationEntry = registeredEntries.find { it.annotation == accessAnnotation }
    return DashboardTab(
      slug = slug,
      url_path_prefix = url_path_prefix,
      dashboard = dashboardAnnotation.simpleName!!,
      name = name,
      category = category,
      capabilities = accessAnnotationEntry?.capabilities?.toSet() ?: capabilities,
      services = accessAnnotationEntry?.services?.toSet() ?: services
    )
  }
}

/** Binds a DashboardTab for Dashboard [DA] with access annotation [AA] */
inline fun <reified DA : Annotation, reified AA : Annotation> DashboardTabAccessProvider(
  slug: String,
  url_path_prefix: String,
  name: String,
  category: String = "Admin"
) = DashboardTabProviderBuilder(
  slug = slug,
  url_path_prefix = url_path_prefix,
  name = name,
  category = category,
  dashboardAnnotation = DA::class,
  accessAnnotation = AA::class
)

/** Binds a DashboardTab for Dashboard [DA] */
inline fun <reified DA : Annotation> DashboardTabProvider(
  slug: String,
  url_path_prefix: String,
  name: String,
  category: String = "Admin",
  capabilities: Set<String> = setOf(),
  services: Set<String> = setOf()
) = DashboardTabProviderBuilder(
  slug = slug,
  url_path_prefix = url_path_prefix,
  name = name,
  category = category,
  capabilities = capabilities,
  services = services,
  dashboardAnnotation = DA::class
)