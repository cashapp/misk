package misk.web.dashboard

import misk.web.dashboard.ValidWebEntry.Companion.slugify

/**
 * A list of elements to add to the dashboard navbar
 * Misk-Web's default navbar handles responsive layout of these items so overflow
 *   navbar items are included in the drop down menu
 */
data class DashboardNavbarItem(
  val dashboard_slug: String,
  val item: String,
  val order: Int
) : ValidWebEntry(valid_slug = dashboard_slug)

inline fun <reified DA : Annotation> DashboardNavbarItem(
  item: String,
  order: Int
) = DashboardNavbarItem(
  dashboard_slug = slugify<DA>(),
  item = item,
  order = order
)
