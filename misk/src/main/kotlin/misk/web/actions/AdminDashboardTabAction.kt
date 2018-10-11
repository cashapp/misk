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
import javax.inject.Qualifier
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
  @Inject @AdminDashboardTab private lateinit var adminDashboardTabs: List<DashboardTab>
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

  data class Response(val adminDashboardTabs: List<DashboardTab>)
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class AdminDashboardTab
