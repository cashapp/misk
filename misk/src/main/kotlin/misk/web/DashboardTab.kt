package misk.web

import misk.security.authz.AccessAnnotationEntry
import misk.web.ValidWebEntry.Companion.slugify
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KClass

/**
 * A [WebTab] with additional fields to bind to a specific DashboardId, with a menu name and category
 */
class DashboardTab(
  slug: String,
  url_path_prefix: String,
  val dashboardSlug: String,
  val name: String,
  val category: String = "Admin",
  capabilities: Set<String> = setOf(),
  services: Set<String> = setOf()
) : WebTab(slug, url_path_prefix, capabilities, services)

/**
 * Create a DashboardTab using the Dashboard Annotation class simple name as the DashboardId string
 */
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
  dashboardSlug = slugify<DA>(),
  name = name,
  category = category,
  capabilities = capabilities,
  services = services
)

/**
 * Sets the tab's dashboardId by annotation and authentication by injected access annotation entry
 */
class DashboardTabProvider(
  val slug: String,
  val url_path_prefix: String,
  val name: String,
  val category: String = "Admin",
  val dashboardSlug: String,
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
      dashboardSlug = dashboardSlug,
      name = name,
      category = category,
      capabilities = accessAnnotationEntry?.capabilities?.toSet() ?: capabilities,
      services = accessAnnotationEntry?.services?.toSet() ?: services
    )
  }
}

/**
 * Binds a DashboardTab for Dashboard [DA] with access annotation [AA]
 */
inline fun <reified DA : Annotation, reified AA : Annotation> DashboardTabProvider(
  slug: String,
  url_path_prefix: String,
  name: String,
  category: String = "Admin"
) = DashboardTabProvider(
  slug = slug,
  url_path_prefix = url_path_prefix,
  name = name,
  category = category,
  dashboardSlug = slugify<DA>(),
  accessAnnotation = AA::class
)
