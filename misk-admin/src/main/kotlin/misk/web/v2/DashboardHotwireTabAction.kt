package misk.web.v2

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.html.h1
import misk.scope.ActionScoped
import misk.turbo.turbo_frame
import misk.web.Get
import misk.web.HttpCall
import misk.web.PathParam
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.dashboard.DashboardTabLoader
import misk.web.dashboard.DashboardTabLoaderEntry
import misk.web.mediatype.MediaTypes

/** Builds dashboard UI and loads Hotwire tab. */
@Singleton
internal class DashboardHotwireTabAction
@Inject
constructor(
  @JvmSuppressWildcards private val clientHttpCall: ActionScoped<HttpCall>,
  private val dashboardPageLayout: DashboardPageLayout,
  private val entries: List<DashboardTabLoaderEntry>,
) : WebAction {
  @Get("/{suffix:.*}")
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @AdminDashboardAccess
  fun get(@PathParam suffix: String?): String =
    dashboardPageLayout.newBuilder().build { _, _, _ ->
      val url = clientHttpCall.get().url
      val fullPath = url.encodedPath
      val entry =
        entries
          .filter { it.loader is DashboardTabLoader.IframeTab }
          .firstOrNull { fullPath.startsWith(it.urlPathPrefix) }
      (entry?.loader as? DashboardTabLoader.HotwireTab)?.urlPathPrefix?.let { urlPathPrefix ->
        turbo_frame(id = "tab") { attributes["src"] = "$urlPathPrefix$suffix${url.encodedQuery}" }
      } ?: h1 { +"""Dashboard tab not found at $fullPath""" }
    }
}
