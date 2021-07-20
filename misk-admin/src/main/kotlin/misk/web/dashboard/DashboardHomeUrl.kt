package misk.web.dashboard

import misk.web.dashboard.ValidWebEntry.Companion.slugify

/**
 * Url to link to for the home button in the dashboard navbar
 * 1 [DashboardHomeUrl] should be bound per dashboard
 */
data class DashboardHomeUrl(
  val dashboard_slug: String,
  val url: String
) : ValidWebEntry(slug = dashboard_slug, url_path_prefix = url)

inline fun <reified DA : Annotation> DashboardHomeUrl(
  urlPathPrefix: String
) = DashboardHomeUrl(
  dashboard_slug = slugify<DA>(),
  url = urlPathPrefix
)
