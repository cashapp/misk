package misk.web.v2

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.html.div
import kotlinx.html.iframe
import misk.scope.ActionScoped
import misk.tailwind.components.AlertError
import misk.tailwind.components.AlertInfo
import misk.web.Get
import misk.web.HttpCall
import misk.web.PathParam
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.dashboard.DashboardTab
import misk.web.dashboard.DashboardTabLoader
import misk.web.dashboard.DashboardTabLoaderEntry
import misk.web.dashboard.MiskWebTabIndexAction
import misk.web.mediatype.MediaTypes
import misk.web.proxy.WebProxyAction
import misk.web.resources.StaticResourceAction
import okhttp3.HttpUrl.Companion.toHttpUrl
import wisp.deployment.Deployment

/**
 * Builds dashboard UI and loads IFrame tab.
 */
@Singleton
internal class DashboardIFrameTabAction @Inject constructor(
  @JvmSuppressWildcards private val clientHttpCall: ActionScoped<HttpCall>,
  private val dashboardPageLayout: DashboardPageLayout,
  private val dashboardTabs: List<DashboardTab>,
  private val deployment: Deployment,
  private val entries: List<DashboardTabLoaderEntry>,
  private val staticResourceAction: StaticResourceAction,
  private val webProxyAction: WebProxyAction,
) : WebAction {
  @Get("/{suffix:.*}")
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @AdminDashboardAccess
  fun get(@PathParam suffix: String?): String = dashboardPageLayout
    .newBuilder()
    .build { _, _, _ ->
      val fullPath = clientHttpCall.get().url.encodedPath
      val entry = entries
        .filter { it.loader is DashboardTabLoader.IframeTab }
        .firstOrNull { fullPath.startsWith(it.urlPathPrefix) }

      val iframeTab = entry?.loader as? DashboardTabLoader.IframeTab

      div("container mx-auto p-8") {
        if (iframeTab != null) {
          // If the tab is found, render the iframe

          if (iframeTab.iframePath.startsWith(MiskWebTabIndexAction.PATH)) {
            // If tab is Misk-Web, do extra validation to show a more helpful error message

            val slug = iframeTab.urlPathPrefix.split("/").last { it.isNotBlank() }
            val dashboardTab = dashboardTabs.firstOrNull { slug == it.slug }

            if (dashboardTab == null) {
              AlertError("No Misk-Web tab found for slug: $slug. Check your install bindings or Misk-Web build.")
            } else {
              // TODO remove this hack when new Web Actions tab lands and old ones removed, v1 and v2 are in the same web-actions tab
              val normalizedSlug =
                if (dashboardTab.slug == "web-actions-v1") "web-actions" else dashboardTab.slug
              val tabEntrypointJs = "/_tab/${normalizedSlug}/tab_${normalizedSlug}.js"

              // If tab is Misk-Web do additional checks and show separate development and real errors
              if (deployment.isLocalDevelopment) {
                // If local development, check web proxy action and show fuller development 404 message
                val tabEntrypointJsResponse =
                  webProxyAction.getResponse(("http://localhost/" + tabEntrypointJs).toHttpUrl())
                if (tabEntrypointJsResponse.statusCode != 200) {
                  AlertError("Failed to load Misk-Web tab: ${dashboardTab.menuCategory} / ${dashboardTab.menuLabel}")
                  AlertInfo("In local development, this can be from not having your local dev server (ie. Webpack) running or not doing an initial local Misk-Web build to generate the necessary web assets. Try running in your Terminal: \$ gradle buildMiskWeb OR \$ misk-web ci-build -e.")
                }
              } else if (deployment.isReal) {
                // If real environment, only check static resource action and show limited 404 message
                val tabEntrypointJsResponse =
                  staticResourceAction.getResponse(("http://localhost/" + tabEntrypointJs).toHttpUrl())
                if (tabEntrypointJsResponse.statusCode != 200) {
                  AlertError("Failed to load Misk-Web tab: ${dashboardTab.menuCategory} / ${dashboardTab.menuLabel}")
                  AlertInfo("In real environments, this is usually because of a web build failure in CI. Try checking CI logs and report this bug to your platform team. If the CI web build fails or is not run, the web assets will be missing from the Docker context when deployed and fail to load.")
                }
              }
            }
          }

          // Always still show iframe so that full load errors show up in browser console
          iframe(classes = "h-full w-full") {
            src = "${iframeTab.iframePath}$suffix"
          }
        } else {
          // If tab is not found show alert error
          AlertError("""Dashboard tab not found at $fullPath""")
        }
      }
    }
}
