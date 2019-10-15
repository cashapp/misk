package misk.web.actions

import misk.MiskCaller
import misk.scope.ActionScoped
import misk.security.authz.Unauthenticated
import misk.web.DashboardTab
import misk.web.Get
import misk.web.PathParam
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.ValidWebEntry
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
  @Inject private lateinit var allHomeUrls: List<DashboardHomeUrl>
  @Inject lateinit var callerProvider: @JvmSuppressWildcards ActionScoped<MiskCaller?>

  @Get("/api/dashboard/{dashboardId}/metadata")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Unauthenticated
  fun getAll(
    @PathParam dashboardId: String
  ): Response {
    val caller = callerProvider.get() ?: return Response()

    val authorizedDashboardTabs = allTabs
      .filter { it.dashboardId == dashboardId }
      .filter { caller.isAllowed(it.capabilities, it.services) }

    val homeUrl = allHomeUrls
      .find { it.dashboardId == dashboardId }?.urlPathPrefix ?: ""

    val navbarItems = allNavbarItems
      .filter { it.dashboardId == dashboardId }
      .sortedBy { it.order }
      .map { it.item }

    val navbarStatus = allNavbarStatus
      .find { it.dashboardId == dashboardId }?.status ?: ""

    val dashboardMetadata = DashboardMetadata(
      home_url = homeUrl,
      navbar_items = navbarItems,
      navbar_status = navbarStatus,
      tabs = authorizedDashboardTabs
    )
    return Response(dashboardMetadata = dashboardMetadata)
  }

  data class DashboardHomeUrl(
    val dashboardId: String,
    val urlPathPrefix: String
  ): ValidWebEntry(url_path_prefix = urlPathPrefix)

  data class DashboardNavbarItem(
    val dashboardId: String,
    val item: String,
    val order: Int
  )

  data class DashboardNavbarStatus(
    val dashboardId: String,
    val status: String
  )

  data class DashboardMetadata(
    val home_url: String = "",
    val navbar_items: List<String> = listOf(),
    val navbar_status: String = "",
    val tabs: List<DashboardTab> = listOf()
  )

  data class Response(val dashboardMetadata: DashboardMetadata = DashboardMetadata())

  companion object {
    inline fun <reified DA : Annotation> DashboardHomeUrl(
      urlPathPrefix: String
    ) = DashboardHomeUrl(
      dashboardId = DA::class.simpleName!!,
      urlPathPrefix = urlPathPrefix
    )

    inline fun <reified DA : Annotation> DashboardNavbarItem(
      item: String,
      order: Int
    ) = DashboardNavbarItem(
      dashboardId = DA::class.simpleName!!,
      item = item,
      order = order
    )

    inline fun <reified DA : Annotation> DashboardNavbarStatus(
      status: String
    ) = DashboardNavbarStatus(
      dashboardId = DA::class.simpleName!!,
      status = status
    )
  }
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class AdminDashboard
