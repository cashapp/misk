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
import okhttp3.OkHttpClient
import okhttp3.Request
import wisp.deployment.Deployment
import misk.logging.getLogger

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
    // Assume that any IFrame tabs use a custom front end so full page reloads on service changes
    //   are more annoying than helpful
    .hotReload(false)
    .build { _, _, _ ->
      val fullPath = clientHttpCall.get().url.encodedPath
      val entry = entries
        .filter { it.loader is DashboardTabLoader.IframeTab }
        .firstOrNull { fullPath.startsWith(it.urlPathPrefix) }

      val iframeTab = entry?.loader as? DashboardTabLoader.IframeTab

      if (iframeTab != null) {
        // If the tab is found, render the iframe

        val iframeSrc = "${iframeTab.iframePath}$suffix"
        val hostname = "http://localhost/"

        if (iframeTab.iframePath.startsWith(MiskWebTabIndexAction.PATH)) {
          // If tab is Misk-Web, do extra validation to show a more helpful error message

          val slug = iframeTab.urlPathPrefix.split("/").last { it.isNotBlank() }
          val dashboardTab = dashboardTabs.firstOrNull { slug == it.slug }

          if (dashboardTab == null) {
            div("container mx-auto p-8") {
              AlertError("No Misk-Web tab found for slug: $slug. Check your install bindings or Misk-Web build.")
            }
          } else {
            val normalizedSlug = dashboardTab.slug
            val tabEntrypointJs = "/_tab/${normalizedSlug}/tab_${normalizedSlug}.js"

            // If tab is Misk-Web do additional checks and show separate development and real errors
            if (deployment.isLocalDevelopment) {
              // If local development, check web proxy action and show fuller development 404 message
              val tabEntrypointJsResponse = webProxyAction
                .getResponse((hostname / tabEntrypointJs).toHttpUrl())
              if (tabEntrypointJsResponse.statusCode != 200) {
                div("container mx-auto p-8") {
                  AlertError("Failed to load Misk-Web tab: ${dashboardTab.menuCategory} / ${dashboardTab.menuLabel}")
                  AlertInfo("In local development, this can be from not having your local dev server (ie. Webpack) running or not doing an initial local Misk-Web build to generate the necessary web assets. Try running in your Terminal: \$ gradle buildMiskWeb OR \$ misk-web ci-build -e.")
                }
              }
            } else if (deployment.isReal) {
              // If real environment, only check static resource action and show limited 404 message
              val tabEntrypointJsResponse = staticResourceAction
                .getResponse((hostname / tabEntrypointJs).toHttpUrl())
              if (tabEntrypointJsResponse.statusCode != 200) {
                logger.info("Failed to load Misk-Web tab: ${dashboardTab.menuCategory} / ${dashboardTab.menuLabel} Response: $tabEntrypointJsResponse")
                div("container mx-auto p-8") {
                  AlertError("Failed to load Misk-Web tab: ${dashboardTab.menuCategory} / ${dashboardTab.menuLabel}")
                  AlertInfo("In real environments, this is usually because of a web build failure in CI. Try checking CI logs and report this bug to your platform team. If the CI web build fails or is not run, the web assets will be missing from the Docker context when deployed and fail to load.")
                }
              }
            }
          }
        } else if (iframeTab.iframePath.endsWith("index.html")) {
          // If tab is a non Misk-Web frontend, do extra validation to show a more helpful error message

          val slug = iframeTab.urlPathPrefix.split("/").last { it.isNotBlank() }
          val dashboardTab = dashboardTabs.firstOrNull { slug == it.slug }

          if (dashboardTab == null) {
            div("container mx-auto p-8") {
              AlertError("No tab found for slug: $slug")
              AlertInfo("Check your DashboardModule installation to ensure that the slug, urlPathPrefix, and iframePath matches your frontend location.")
            }
          } else {
            if (deployment.isLocalDevelopment) {
              // If local development, check web proxy action and show fuller development 404 message
              val proxyResponse = webProxyAction
                .getResponse((hostname / iframeTab.iframePath).toHttpUrl())
              val staticResponse = staticResourceAction
                .getResponse((hostname / iframeTab.iframePath).toHttpUrl())
              if (proxyResponse.statusCode != 200 && staticResponse.statusCode != 200) {
                div("container mx-auto p-8") {
                  AlertError("Failed to load tab: ${dashboardTab.menuCategory} / ${dashboardTab.menuLabel}")
                  if (iframeTab.iframePath == "/_tab/web-actions/index.html") {
                    AlertInfo("To use the Web Actions tab in local development, start the dev server (run in your terminal: \$ cd misk/misk-admin-web-actions/ && npm start) or run a build to generate the necessary web assets (run in your terminal: \$ gradle :misk:misk-admin:buildAndCopyWebActions).")
                  } else {
                    AlertInfo("In local development, this can be from not having your local dev server (ie. Webpack) running or not doing an initial local frontend build to generate the necessary web assets. Try running in your Terminal: \$ gradle buildMiskWeb OR \$ misk-web ci-build -e.")
                  }
                }
              }
            } else if (deployment.isReal) {
              // If real environment, only check static resource action and show limited 404 message
              val tabEntrypointJsResponse = staticResourceAction
                .getResponse((hostname / iframeTab.iframePath).toHttpUrl())
              if (tabEntrypointJsResponse.statusCode != 200) {
                logger.info("Failed to load tab: ${dashboardTab.menuCategory} / ${dashboardTab.menuLabel} Response: $tabEntrypointJsResponse")
                div("container mx-auto p-8") {
                  AlertError("Failed to load tab: ${dashboardTab.menuCategory} / ${dashboardTab.menuLabel}")
                  AlertInfo("In real environments, this is usually because of a web build failure in CI. Try checking CI logs and report this bug to your platform team. If the CI web build fails or is not run, the web assets will be missing from the Docker context when deployed and fail to load.")
                }
              }
            }
          }
        } else {
          // If tab is not Misk-Web or JS frontend, show generic error message if src doesn't resolve
          try {
            OkHttpClient.Builder().build().newCall(Request((hostname / iframeSrc).toHttpUrl()))
              .execute()
              .use { response ->
                if (!response.isSuccessful) {
                  div("container mx-auto p-8") {
                    AlertError("Failed to load tab at $iframeSrc")
                  }
                }
              }
          } catch (e: Exception) {
            div("container mx-auto p-8") {
              AlertError("Failed to load tab at $iframeSrc")
            }
          }
        }

        // Always still show iframe so that full load errors show up in browser console
        iframe(classes = "h-full w-full") {
          src = iframeSrc
        }
      } else {
        div("container mx-auto p-8") {
          // If tab is not found show alert error
          AlertError("""Dashboard tab not found at $fullPath""")
        }
      }
    }

  // Allow easier concatenation of URL paths with auto-stripping of extra slashes
  private operator fun String.div(other: String): String = StringBuilder()
    .append(this.removeSuffix("/"))
    .append("/")
    .append(other.removePrefix("/"))
    .toString()

  companion object {
    private val logger = getLogger<DashboardIFrameTabAction>()
  }
}
