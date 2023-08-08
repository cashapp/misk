package misk.web.v2

import kotlinx.html.TagConsumer
import misk.config.AppName
import misk.hotwire.buildHtml
import misk.scope.ActionScoped
import misk.tailwind.Link
import misk.tailwind.pages.MenuSection
import misk.tailwind.pages.Navbar
import misk.turbo.turbo_frame
import misk.web.HttpCall
import misk.web.dashboard.DashboardHomeUrl
import misk.web.dashboard.DashboardNavbarItem
import misk.web.dashboard.DashboardTab
import misk.web.v2.DashboardIndexAction.Companion.titlecase
import wisp.deployment.Deployment
import jakarta.inject.Inject

/**
 * Builds dashboard UI for index homepage.
 *
 * Must be called within a Web Action.
 */
class DashboardPageLayout @Inject constructor(
  private val allHomeUrls: List<DashboardHomeUrl>,
  @AppName private val appName: String,
  private val allNavbarItem: List<DashboardNavbarItem>,
  private val allTabs: List<DashboardTab>,
  private val deployment: Deployment,
  @JvmSuppressWildcards private val clientHttpCall: ActionScoped<HttpCall>,
) {
  private var newBuilder = false
  private fun setNewBuilder() = apply { newBuilder = true }
  fun newBuilder(): DashboardPageLayout = DashboardPageLayout(
    allHomeUrls = allHomeUrls,
    appName = appName,
    allNavbarItem = allNavbarItem,
    allTabs = allTabs,
    deployment = deployment,
    clientHttpCall = clientHttpCall
  ).setNewBuilder()

  private var title: (appName: String, dashboardHomeUrl: DashboardHomeUrl?, dashboardTab: DashboardTab?) -> String =
    { appName: String, dashboardHomeUrl: DashboardHomeUrl?, dashboardTab: DashboardTab? -> "${dashboardTab?.menuLabel?.let { "$it | " } ?: ""}${dashboardTab?.menuCategory} on $appName ${dashboardHomeUrl?.dashboardAnnotationKClass?.titlecase() ?: ""}" }

  fun title(title: (appName: String, dashboardHomeUrl: DashboardHomeUrl?, dashboardTab: DashboardTab?) -> String) = apply {
    this.title = title
  }

  private var path: String = clientHttpCall.get().url.encodedPath
  fun path(path: String) = apply { this.path = path }

  @JvmOverloads
  fun build(tabBlock: TagConsumer<*>.(appName: String, dashboardHomeUrl: DashboardHomeUrl?, dashboardTab: DashboardTab?) -> Unit = { _, _, _ -> Unit }): String {
    check(newBuilder) {
      "You must call newBuilder() before calling build() to prevent builder reuse."
    }
    newBuilder = false

    val dashboardHomeUrl = allHomeUrls
      .firstOrNull { path.startsWith(it.url) }
    val homeUrl = dashboardHomeUrl?.url ?: "/"
    val dashboardTab = allTabs
      // TODO make this startsWith after v2 lands
      .firstOrNull { path.contains(it.url_path_prefix) }
    val menuSections =
      toMenuSections(
        allNavbarItem.filter { dashboardHomeUrl?.dashboard_slug == it.dashboard_slug },
        allTabs.filter { dashboardHomeUrl?.dashboard_slug == it.dashboard_slug },
        path
      )

    return buildHtml {
      HtmlLayout(
        homeUrl,
        title(appName, dashboardHomeUrl, dashboardTab),
        deployment.isLocalDevelopment
      ) {
        Navbar(
          appName = appName,
          deployment = deployment,
          homeHref = homeUrl,
          menuSections = menuSections
        ) {
          // TODO make this a src so it loads page changes with turbo
          turbo_frame(id = "tab") {
            tabBlock(appName, dashboardHomeUrl, dashboardTab)
          }
        }
      }
    }
  }

  fun toMenuSections(
    navbarItems: List<DashboardNavbarItem>,
    dashboardTabs: List<DashboardTab>,
    currentPath: String
  ) = if (navbarItems.isNotEmpty()) {
    listOf(
      MenuSection(
        title = "Links",
        links = navbarItems
          // Filter out tabs so duplicate links aren't showing up in the nav menu
          .filterNot { dashboardTabs.any { tab -> it.item.contains(tab.slug) } }
          .map {
            Link(href = "", label = "", rawHtml = it.item)
          }
      )
    )
  } else {
    listOf()
  } +
    dashboardTabs.groupBy { it.menuCategory }.map { (sectionTitle, dashboardTabs) ->
      MenuSection(
        title = sectionTitle,
        links = dashboardTabs.map {
          Link(
            label = it.menuLabel,
            // TODO remove /_admin/ hack when old dashboard is removed
            href = if (it.menuUrl == "$ADMIN_DASHBOARD_PATH/" || !it.menuUrl.contains(
                "$ADMIN_DASHBOARD_PATH/"
              )) it.menuUrl else "$BETA_PREFIX${it.menuUrl}",
            // TODO remove /_admin/ hack when old dashboard is removed
            isSelected = if (it.menuUrl == "$ADMIN_DASHBOARD_PATH/") false else currentPath.startsWith(
              "$BETA_PREFIX${it.menuUrl}"
            ),
            isPageNavigation = true,
          )
        }
      )
    }

  companion object {
    const val ADMIN_DASHBOARD_PATH = "/_admin"
    const val BETA_PREFIX = "/v2"

  }
}
