package misk.web.metadata

import misk.MiskCaller
import misk.scope.ActionScoped
import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.PathParam
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.DashboardHomeUrl
import misk.web.dashboard.DashboardNavbarItem
import misk.web.dashboard.DashboardNavbarStatus
import misk.web.dashboard.DashboardTab
import misk.web.dashboard.DashboardTheme
import misk.web.dashboard.MiskWebTheme
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serve metadata related to a Misk-Web Dashboard
 *
 * Multiple dashboards with Misk-Web tabs can be bound by binding tabs to a different
 *   Dashboard Annotation. For example, all tabs used in the Misk Admin Dashboard are
 *   bound with the [AdminDashboard] annotation; tabs used in a service front
 *   end app, like Backfila, would be bound with a "BackfilaApp" annotation.
 *
 * Dashboard related metadata is multibound with the slug that corresponds to
 *   a Dashboard Annotation. [DashboardMetadataAction] returns only the metadata for
 *   the requested dashboard.
 */
@Singleton
class DashboardMetadataAction @Inject constructor() : WebAction {
  @Inject private lateinit var allTabs: List<DashboardTab>
  @Inject private lateinit var allNavbarItems: List<DashboardNavbarItem>
  @Inject private lateinit var allNavbarStatus: List<DashboardNavbarStatus>
  @Inject private lateinit var allHomeUrls: List<DashboardHomeUrl>
  @Inject private lateinit var allThemes: List<DashboardTheme>
  @Inject lateinit var callerProvider: @JvmSuppressWildcards ActionScoped<MiskCaller?>

  @Get("/api/dashboard/{dashboard_slug}/metadata")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Unauthenticated
  fun getAll(
    @PathParam dashboard_slug: String
  ): Response {
    val caller = callerProvider.get() ?: return Response()

    val authorizedDashboardTabs = allTabs
      .filter { it.dashboard_slug == dashboard_slug }
      .filter { caller.isAllowed(it.capabilities, it.services) }

    val homeUrl = allHomeUrls
      .find { it.dashboard_slug == dashboard_slug }?.url ?: ""

    val navbarItems = allNavbarItems
      .filter { it.dashboard_slug == dashboard_slug }
      .sortedBy { it.order }
      .map { it.item }

    val navbarStatus = allNavbarStatus
      .find { it.dashboard_slug == dashboard_slug }?.status ?: ""

    val theme = allThemes
      .find { it.dashboard_slug == dashboard_slug }?.theme

    val dashboardMetadata = DashboardMetadata(
      home_url = homeUrl,
      navbar_items = navbarItems,
      navbar_status = navbarStatus,
      tabs = authorizedDashboardTabs,
      theme = theme,
    )
    return Response(
      dashboardMetadata = dashboardMetadata
    )
  }

  data class DashboardMetadata(
    val home_url: String = "",
    val navbar_items: List<String> = listOf(),
    val navbar_status: String = "",
    val tabs: List<DashboardTab> = listOf(),
    /** If null, uses default theme that ships with Misk-Web */
    val theme: MiskWebTheme? = null,
  )

  data class Response(val dashboardMetadata: DashboardMetadata = DashboardMetadata())
}
