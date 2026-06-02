package misk.web.dashboard

import com.google.inject.Provider
import jakarta.inject.Inject
import kotlin.reflect.KClass
import misk.config.AppName
import misk.security.authz.AccessAnnotationEntry
import misk.web.dashboard.ValidWebEntry.Companion.slugify
import wisp.deployment.Deployment

/**
 * A [WebTab] with additional fields to bind to a specific Dashboard that has a tabs menu
 *
 * @property [slug] A unique slug to identify the tab namespace. Note: this slug must match the slug for the tab's
 *   corresponding [WebTabResourceModule]
 * @property [url_path_prefix] A unique url path prefix to namespace tab URLs
 * @property [dashboard_slug] A slug that identifies which dashboard the tab is installed to, generated from a slugified
 *   Dashboard Annotation class simple name
 * @property [menuLabel] A title case name used in the dashboard menu for the link to the tab
 * @property [menuCategory] A title case category used to group tabs in the dashboard menu
 * @property [menuUrl] Url to the tab, by default [url_path_prefix]
 * @property [capabilities] Set to show the tab only for authenticated capabilities, else shows always
 * @property [services] Set to show the tab only for authenticated services, else shows always
 * @property [menuDisableTurboPreload] Disable Turbo link preload for the tab menu link in the navbar
 */
data class DashboardTab
@JvmOverloads
constructor(
  override val slug: String,
  override val url_path_prefix: String,
  val dashboard_slug: String,
  val menuLabel: String,
  val menuCategory: String = "",
  val menuUrl: String = url_path_prefix,
  override val capabilities: Set<String> = setOf(),
  override val services: Set<String> = setOf(),
  val accessAnnotationKClass: KClass<out Annotation>? = null,
  val dashboardAnnotationKClass: KClass<out Annotation>? = null,
  val menuDisableTurboPreload: Boolean = false,
) : WebTab(slug, url_path_prefix, capabilities, services)

/** Sets the tab's authentication capabilities/services by the multibound [AccessAnnotationEntry] */
class DashboardTabProvider
@JvmOverloads
constructor(
  val slug: String,
  val url_path_prefix: String,
  val menuLabel: (appName: String, deployment: Deployment) -> String,
  val menuUrl: (appName: String, deployment: Deployment) -> String = { _, _ -> url_path_prefix },
  val menuCategory: String = "Admin",
  val dashboard_slug: String,
  val capabilities: Set<String> = setOf(),
  val services: Set<String> = setOf(),
  val accessAnnotationKClass: KClass<out Annotation>? = null,
  val dashboardAnnotationKClass: KClass<out Annotation>,
  val menuDisableTurboPreload: Boolean = false,
) : Provider<DashboardTab>, ValidWebEntry(slug, url_path_prefix) {
  @Inject @AppName lateinit var appName: String
  @Inject lateinit var deployment: Deployment
  @Inject lateinit var accessAnnotationEntries: List<AccessAnnotationEntry>

  override fun get(): DashboardTab {
    val accessAnnotationEntry = accessAnnotationEntries.find { it.annotation == accessAnnotationKClass }
    return DashboardTab(
      slug = slug,
      url_path_prefix = url_path_prefix,
      dashboard_slug = dashboard_slug,
      menuLabel = menuLabel(appName, deployment),
      menuUrl = menuUrl(appName, deployment),
      menuCategory = menuCategory,
      capabilities = accessAnnotationEntry?.capabilities?.toSet() ?: capabilities,
      services = accessAnnotationEntry?.services?.toSet() ?: services,
      accessAnnotationKClass = accessAnnotationKClass,
      dashboardAnnotationKClass = dashboardAnnotationKClass,
      menuDisableTurboPreload = menuDisableTurboPreload,
    )
  }
}

/** Binds a DashboardTab for Dashboard [DA] with optional access capabilities and services */
inline fun <reified DA : Annotation> DashboardTabProvider(
  slug: String,
  url_path_prefix: String,
  name: String,
  menuUrl: String = url_path_prefix,
  category: String = "Admin",
  capabilities: Set<String> = setOf(),
  services: Set<String> = setOf(),
  menuDisableTurboPreload: Boolean = false,
) =
  DashboardTabProvider(
    slug = slug,
    url_path_prefix = url_path_prefix,
    menuLabel = { _, _ -> name },
    menuCategory = category,
    menuUrl = { _, _ -> menuUrl },
    dashboard_slug = slugify<DA>(),
    capabilities = capabilities,
    services = services,
    dashboardAnnotationKClass = DA::class,
    menuDisableTurboPreload = menuDisableTurboPreload,
  )

/** Binds a DashboardTab for Dashboard [DA] with access annotation [AA] */
inline fun <reified DA : Annotation, reified AA : Annotation> DashboardTabProvider(
  slug: String,
  url_path_prefix: String,
  name: String,
  menuUrl: String = url_path_prefix,
  category: String = "Admin",
  menuDisableTurboPreload: Boolean = false,
) =
  DashboardTabProvider(
    slug = slug,
    url_path_prefix = url_path_prefix,
    menuLabel = { _, _ -> name },
    menuCategory = category,
    menuUrl = { _, _ -> menuUrl },
    dashboard_slug = slugify<DA>(),
    accessAnnotationKClass = AA::class,
    dashboardAnnotationKClass = DA::class,
    menuDisableTurboPreload = menuDisableTurboPreload,
  )
