package misk.web.actions

import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.WebTab
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Returns all Admin Tabs for primary use in dynamically building the menu bar of the /_admin dashboard
 *
 * Guidelines
 * - name should start with a capital letter unless it is a proper noun (ie. iOS)
 * - category is a string, no enforcement on consistency of names
 *
 */

@Singleton
class AdminDashboardTabAction : WebAction {
  @Inject lateinit var registeredDashboardTabs: List<AdminDashboardTab>

  @Get("/api/admindashboardtabs")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Unauthenticated
  fun getAll(): Response {
    return Response(adminDashboardTabs = registeredDashboardTabs)
  }

  data class Response(val adminDashboardTabs: List<AdminDashboardTab>)
}

class AdminDashboardTab(
  val name: String,
  slug: String,
  url_path_prefix: String,
  val category: String = "Container Admin"
) : WebTab(slug = slug, url_path_prefix = url_path_prefix)

