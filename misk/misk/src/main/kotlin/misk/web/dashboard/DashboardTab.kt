package misk.web.dashboard

import misk.security.authz.AccessAnnotationEntry
import misk.web.dashboard.ValidWebEntry.Companion.slugify
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KClass

/**
 * A [WebTab] with additional fields to bind to a specific Dashboard that has a tabs menu
 *
 * @property [slug] A unique slug to identify the tab namespace.
 *    Note: this slug must match the slug for the tab's corresponding [WebTabResourceModule]
 * @property [url_path_prefix] A unique url path prefix to namespace tab URLs
 * @property [dashboard_slug] A slug that identifies which dashboard the tab is installed to,
 *  generated from a slugified Dashboard Annotation class simple name
 * @property [name] A title case name used in the dashboard menu for the link to the tab
 * @property [category] A title case category used to group tabs in the dashboard menu
 * @property [capabilities] Set to show the tab only for authenticated capabilities, else shows always
 * @property [services] Set to show the tab only for authenticated services, else shows always
 */
class DashboardTab(
  slug: String,
  url_path_prefix: String,
  val dashboard_slug: String,
  val name: String,
  val category: String = "",
  capabilities: Set<String> = setOf(),
  services: Set<String> = setOf()
) : WebTab(slug, url_path_prefix, capabilities, services)

/**
 * Sets the tab's authentication capabilities/services by the multibound [AccessAnnotationEntry]
 */
class DashboardTabProvider(
  val slug: String,
  val url_path_prefix: String,
  val name: String,
  val category: String = "Admin",
  val dashboard_slug: String,
  val accessAnnotation: KClass<out Annotation>? = null,
  val capabilities: Set<String> = setOf(),
  val services: Set<String> = setOf()
) : Provider<DashboardTab> {
  @Inject lateinit var accessAnnotationEntries: List<AccessAnnotationEntry>

  override fun get(): DashboardTab {
    val accessAnnotationEntry = accessAnnotationEntries.find { it.annotation == accessAnnotation }
    return DashboardTab(
      slug = slug,
      url_path_prefix = url_path_prefix,
      dashboard_slug = dashboard_slug,
      name = name,
      category = category,
      capabilities = accessAnnotationEntry?.capabilities?.toSet() ?: capabilities,
      services = accessAnnotationEntry?.services?.toSet() ?: services
    )
  }
}

/**
 * Binds a DashboardTab for Dashboard [DA] with optional access capabilities and services
 */
inline fun <reified DA : Annotation> DashboardTabProvider(
  slug: String,
  url_path_prefix: String,
  name: String,
  category: String = "Admin",
  capabilities: Set<String> = setOf(),
  services: Set<String> = setOf()
) = DashboardTabProvider(
  slug = slug,
  url_path_prefix = url_path_prefix,
  name = name,
  category = category,
  dashboard_slug = slugify<DA>(),
  capabilities = capabilities,
  services = services
)

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
  dashboard_slug = slugify<DA>(),
  accessAnnotation = AA::class
)
