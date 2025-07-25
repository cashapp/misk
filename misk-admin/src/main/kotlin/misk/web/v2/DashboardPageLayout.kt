package misk.web.v2

import jakarta.inject.Inject
import kotlinx.html.TagConsumer
import misk.MiskCaller
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
import wisp.logging.getLogger

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
  private val callerProvider: ActionScoped<MiskCaller?>,
  private val deployment: Deployment,
  private val clientHttpCall: ActionScoped<HttpCall>,
) {
  private var newBuilder = false
  private var headBlock: TagConsumer<*>.() -> Unit = {}
  private var title: (appName: String, dashboardHomeUrl: DashboardHomeUrl?, dashboardTab: DashboardTab?) -> String =
    { appName: String, dashboardHomeUrl: DashboardHomeUrl?, dashboardTab: DashboardTab? -> "${dashboardTab?.menuLabel?.let { "$it | " } ?: ""}${dashboardTab?.menuCategory} on $appName ${dashboardHomeUrl?.dashboardAnnotationKClass?.titlecase() ?: ""}" }

  private val path by lazy {
    clientHttpCall.get().url.encodedPath
  }
  private val dashboardHomeUrl by lazy {
    allHomeUrls.firstOrNull { path.startsWith(it.url) }
  }
  private val homeUrl by lazy {
    dashboardHomeUrl?.url ?: "/"
  }

  private fun setNewBuilder() = apply { newBuilder = true }

  fun newBuilder(): DashboardPageLayout = DashboardPageLayout(
    allHomeUrls = allHomeUrls,
    appName = appName,
    allNavbarItem = allNavbarItem,
    allTabs = allTabs,
    callerProvider = callerProvider,
    deployment = deployment,
    clientHttpCall = clientHttpCall,
  ).setNewBuilder()

  fun title(title: (appName: String, dashboardHomeUrl: DashboardHomeUrl?, dashboardTab: DashboardTab?) -> String) =
    apply {
      this.title = title
    }

  fun headBlock(block: TagConsumer<*>.() -> Unit) = apply { this.headBlock = block }

  @JvmOverloads
  fun build(tabBlock: TagConsumer<*>.(appName: String, dashboardHomeUrl: DashboardHomeUrl?, dashboardTab: DashboardTab?) -> Unit = { _, _, _ -> Unit }): String {
    check(newBuilder) {
      "You must call newBuilder() before calling build() to prevent builder reuse."
    }
    newBuilder = false

    val dashboardTab = allTabs
      // TODO make this startsWith after v2 lands
      .firstOrNull { path.contains(it.url_path_prefix) }
    val menuSections = buildMenuSections(
      navbarItems = allNavbarItem.filter { dashboardHomeUrl?.dashboard_slug == it.dashboard_slug },
      dashboardTabs = allTabs.filter { dashboardHomeUrl?.dashboard_slug == it.dashboard_slug },
      currentPath = path
    )

    val analyticsTitle = if (dashboardTab != null) " ${dashboardTab.menuCategory}/${dashboardTab.menuLabel}" else ""
    logger.info("${callerProvider.get()} visited dashboard tab$analyticsTitle at $path [dashboard=${dashboardHomeUrl?.dashboardAnnotationKClass?.simpleName}]")

    return buildHtml {
      HtmlLayout(
        appRoot = homeUrl,
        title = title(appName, dashboardHomeUrl, dashboardTab),
        playCdn = deployment.isLocalDevelopment,
        headBlock = headBlock,
        hotReload = false,
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

  private fun buildMenuSections(
    navbarItems: List<DashboardNavbarItem>,
    dashboardTabs: List<DashboardTab>,
    currentPath: String,
  ) = if (navbarItems.isNotEmpty()) {
    val links = navbarItems
      // Filter out tabs so duplicate links aren't showing up in the nav menu
      .filterNot { dashboardTabs.any { tab -> it.item.contains(tab.slug) } }
      .map {
        Link(href = "", label = "", rawHtml = it.item)
      }
    if (links.isNotEmpty()) {
      listOf(
        MenuSection(
          title = "Links",
          links = links
        )
      )
    } else {
      listOf()
    }
  } else {
    listOf()
  } + dashboardTabs.groupBy { it.menuCategory }.map { (sectionTitle, dashboardTabs) ->
    MenuSection(
      title = sectionTitle,
      links = dashboardTabs.map {
        val isExternalLink = it.menuUrl.startsWith("https://")
        val isAuthorized = callerProvider.get()?.hasCapability(it.capabilities) == true
        Link(
          label = it.menuLabel,
          href = if (isAuthorized) it.menuUrl else homeUrl,
          isSelected = currentPath.startsWith(it.menuUrl),
          openInNewTab = isExternalLink,
          dataTurbo = !isExternalLink,
          hoverText = if (!isAuthorized) "You do not have access to this tab." else null,
        )
      }
    )
  }

  companion object {
    const val ADMIN_DASHBOARD_PATH = "/_admin"

    private val logger = getLogger<DashboardPageLayout>()
  }
}
