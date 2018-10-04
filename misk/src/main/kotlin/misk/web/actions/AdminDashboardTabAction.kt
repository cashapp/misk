package misk.web.actions

import misk.MiskCaller
import misk.scope.ActionScoped
import misk.security.authz.Unauthenticated
import misk.web.DashboardTab
import misk.web.Get
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.WebTab
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Returns list of all Admin Tabs visible to current calling authenticated user
 *
 * Used in computing topbar nav menu and in inferring tab compiled JS paths
 *
 * Guidelines
 * - name should start with a capital letter unless it is a proper noun (ie. iOS)
 * - category is a string, no enforcement on consistency of names
 */

@Singleton
class AdminDashboardTabAction : WebAction {
  @Inject lateinit var adminDashboardTabs: List<AdminDashboardTab>
  @Inject lateinit var callerProvider: @JvmSuppressWildcards ActionScoped<MiskCaller?>

  @Get("/api/admindashboardtabs")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Unauthenticated
  fun getAll(): Response {
    val caller = callerProvider.get()
    val authorizedAdminDashboardTabs = adminDashboardTabs.filter { it.isAuthenticated(caller)}
    return Response(adminDashboardTabs = authorizedAdminDashboardTabs)
  }

  data class Response(val adminDashboardTabs: List<AdminDashboardTab>)
}

class AdminDashboardTab(
  slug: String,
  url_path_prefix: String,
  roles: Set<String> = setOf(),
  services: Set<String> = setOf(),
  name: String,
  category: String = "Container Admin"
) : DashboardTab(slug = slug, url_path_prefix = url_path_prefix, roles = roles, services = services, name = name, category = category)

