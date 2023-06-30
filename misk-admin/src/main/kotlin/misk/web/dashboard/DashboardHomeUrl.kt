package misk.web.dashboard

import misk.web.dashboard.ValidWebEntry.Companion.slugify
import kotlin.reflect.KClass

/**
 * Url to link to for the home button in the dashboard navbar
 * 1 [DashboardHomeUrl] should be bound per dashboard
 */
data class DashboardHomeUrl(
  val dashboard_slug: String,
  val url: String,
  val dashboardAnnotationKClass: KClass<out Annotation>? = null
) : ValidWebEntry(valid_slug = dashboard_slug, valid_url_path_prefix = url)

inline fun <reified DA : Annotation> DashboardHomeUrl(
  urlPathPrefix: String
) = DashboardHomeUrl(
  dashboard_slug = slugify<DA>(),
  url = urlPathPrefix,
  dashboardAnnotationKClass = DA::class
)
