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
  @Inject private lateinit var allTabs: List<DashboardTab>
  @Inject private lateinit var allNavbarItems: List<DashboardNavbarItem>
  @Inject private lateinit var allNavbarStatus: List<DashboardNavbarStatus>
  @Inject lateinit var callerProvider: @JvmSuppressWildcards ActionScoped<MiskCaller?>

  @Get("/api/dashboard/metadata/{dashboard}")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Unauthenticated
  fun getAll(
    @PathParam dashboard: String
  ): Response {
    val caller = callerProvider.get() ?: return Response()

    val dashboardTabs = allTabs.filter { it.dashboard == dashboard }
    val authorizedDashboardTabs =
      dashboardTabs.filter { caller.isAllowed(it.capabilities, it.services) }

    val navbarItems = allNavbarItems
      .filter { it.dashboard == dashboard }
      .sortedBy { it.order }
      .map {it.item}

    val navbarStatus = allNavbarStatus.find { it.dashboard == dashboard }?.status ?: ""

    return Response(navbar_items = navbarItems, navbar_status = navbarStatus, tabs = authorizedDashboardTabs)
  }

  data class DashboardNavbarItem(
    val dashboard: String,
    val item: String,
    val order: Int
  )

  inline fun <reified DA : Annotation> DashboardNavbarItem(
    item: String,
    order: Int
  ) = DashboardNavbarItem(
    dashboard = DA::class.simpleName!!,
    item = item,
    order = order
  )

  data class DashboardNavbarStatus(
    val dashboard: String,
    val status: String
  )

  data class Response(
    val navbar_items: List<String> = listOf(),
    val navbar_status: String = "",
    val tabs: List<DashboardTab> = listOf()
  )
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class AdminDashboardTab
