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
import misk.web.metadata.DashboardMetadataAction.DashboardTabMetadata.Companion.toMetadata
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
class DashboardMetadataAction @Inject constructor(
  private val allTabs: List<DashboardTab>,
  private val allNavbarItems: List<DashboardNavbarItem>,
  private val allNavbarStatus: List<DashboardNavbarStatus>,
  private val allHomeUrls: List<DashboardHomeUrl>,
  private val allThemes: List<DashboardTheme>,
  private val callerProvider: ActionScoped<MiskCaller?>
) : WebAction {
  @Get("/api/dashboard/{dashboard_slug}/metadata")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Unauthenticated
  fun getAll(
    @PathParam dashboard_slug: String
  ): Response {
    return Response(getDashboardMetadata(callerProvider.get(), dashboard_slug))
  }

  fun getDashboardMetadata(
    caller: MiskCaller?,
    dashboardSlug: String
  ): DashboardMetadata {
    if (caller == null) return DashboardMetadata()

    val authorizedDashboardTabs = allTabs
      .filter { it.dashboard_slug == dashboardSlug }
      .filter { caller.isAllowed(it.capabilities, it.services) }

    val homeUrl = allHomeUrls
      .find { it.dashboard_slug == dashboardSlug }?.url ?: ""

    val navbarItems = allNavbarItems
      .filter { it.dashboard_slug == dashboardSlug }
      .sortedBy { it.order }
      .map { it.item }

    val navbarStatus = allNavbarStatus
      .find { it.dashboard_slug == dashboardSlug }?.status ?: ""

    val theme = allThemes
      .find { it.dashboard_slug == dashboardSlug }?.theme

    return DashboardMetadata(
      home_url = homeUrl,
      navbar_items = navbarItems,
      navbar_status = navbarStatus,
      tabs = authorizedDashboardTabs.map { it.toMetadata() },
      theme = theme,
    )
  }

  data class DashboardTabMetadata(
    val slug: String,
    val url_path_prefix: String,
    val dashboard_slug: String,
    val name: String,
    val category: String = "",
    val capabilities: Set<String> = setOf(),
    val services: Set<String> = setOf(),
  ) {
    companion object {
      fun DashboardTab.toMetadata() = DashboardTabMetadata(
        slug = slug,
        url_path_prefix = url_path_prefix,
        dashboard_slug = dashboard_slug,
        name = name,
        category = category,
        capabilities = capabilities,
        services = services
      )
    }
  }

  data class DashboardMetadata(
    val home_url: String = "",
    val navbar_items: List<String> = listOf(),
    val navbar_status: String = "",
    val tabs: List<DashboardTabMetadata> = listOf(),
    /** If null, uses default theme that ships with Misk-Web */
    val theme: MiskWebTheme? = null,
  )

  data class Response(val dashboardMetadata: DashboardMetadata = DashboardMetadata())
}
