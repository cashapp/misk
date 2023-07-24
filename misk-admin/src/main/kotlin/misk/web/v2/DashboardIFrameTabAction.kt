package misk.web.v2

import kotlinx.html.h1
import kotlinx.html.iframe
import misk.scope.ActionScoped
import misk.web.Get
import misk.web.HttpCall
import misk.web.PathParam
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.dashboard.DashboardTabLoader
import misk.web.dashboard.DashboardTabLoaderEntry
import misk.web.mediatype.MediaTypes
import com.google.inject.Inject
import com.google.inject.Singleton

/**
 * Builds dashboard UI and loads IFrame tab.
 */
@Singleton
class DashboardIFrameTabAction @Inject constructor(
  @JvmSuppressWildcards private val clientHttpCall: ActionScoped<HttpCall>,
  private val dashboardPageLayout: DashboardPageLayout,
  private val entries: List<DashboardTabLoaderEntry>,
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
      (entry?.loader as? DashboardTabLoader.IframeTab)?.let {
        iframe(classes = "h-full w-full") {
          src = "${it.iframePath}$suffix"
        }
      } ?: h1 { +"""Dashboard tab not found at $fullPath""" }
    }
}
