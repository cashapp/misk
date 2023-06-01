package misk.web.dashboard

import misk.web.dashboard.ValidWebEntry.Companion.slugify

/**
 * Banner text to show below the dashboard navbar
 * 0 or 1 [DashboardNavbarStatus] should be bound per dashboard
 */
@Deprecated("DashboardNavbarStatus will not be supported in Misk Dashboards v2 and will be deleted shortly. Use custom UI to surface status information.")
data class DashboardNavbarStatus(
  val dashboard_slug: String,
  val status: String
) : ValidWebEntry(valid_slug = dashboard_slug)

@Deprecated("DashboardNavbarStatus will not be supported in Misk Dashboards v2 and will be deleted shortly. Use custom UI to surface status information.")
inline fun <reified DA : Annotation> DashboardNavbarStatus(
  status: String
) = DashboardNavbarStatus(
  dashboard_slug = slugify<DA>(),
  status = status
)
