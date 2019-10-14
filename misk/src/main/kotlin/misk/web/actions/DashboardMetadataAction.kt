package misk.web.actions

import misk.MiskCaller
import misk.scope.ActionScoped
import misk.security.authz.Unauthenticated
import misk.web.DashboardTab
import misk.web.Get
import misk.web.PathParam
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Returns list of all authenticated dashboard tabs for a given [dashboard]
 *
 * Used in computing topbar nav menu and in inferring tab compiled JS paths
 *
 * Guidelines
 * - name should start with a capital letter unless it is a proper noun (ie. iOS)
 * - category is a string, no enforcement on consistency of names
 */

@Singleton
class DashboardMetadataAction @Inject constructor() : WebAction {
  @Inject private lateinit var allDashboardTabs: List<DashboardTab>
  @Inject lateinit var callerProvider: @JvmSuppressWildcards ActionScoped<MiskCaller?>

  @Get("/api/admindashboardtabs/{dashboard}")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Unauthenticated
  fun getAll(
    @PathParam dashboard: String
  ): Response {
    val caller = callerProvider.get() ?: return Response()
    val dashboardTabs = allDashboardTabs.filter { it.dashboard == dashboard }
    val authorizedDashboardTabs =
      dashboardTabs.filter { caller.isAllowed(it.capabilities, it.services) }
    return Response(adminDashboardTabs = authorizedDashboardTabs)
  }

  data class Response(val adminDashboardTabs: List<DashboardTab> = listOf())
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class AdminDashboardTab
