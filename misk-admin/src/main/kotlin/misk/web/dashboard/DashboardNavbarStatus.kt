package misk.web.dashboard

import misk.web.dashboard.ValidWebEntry.Companion.slugify

/**
 * Banner text to show below the dashboard navbar
 * 0 or 1 [DashboardNavbarStatus] should be bound per dashboard
 */
data class DashboardNavbarStatus(
  val dashboard_slug: String,
  val status: String
) : ValidWebEntry(valid_slug = dashboard_slug)

inline fun <reified DA : Annotation> DashboardNavbarStatus(
  status: String
) = DashboardNavbarStatus(
  dashboard_slug = slugify<DA>(),
  status = status
)
