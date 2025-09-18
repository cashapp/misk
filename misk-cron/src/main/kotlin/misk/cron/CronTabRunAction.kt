package misk.cron

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.html.div
import misk.tailwind.components.AlertError
import misk.web.Get
import misk.web.PathParam
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import misk.web.v2.DashboardPageLayout
import okhttp3.Headers
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_MOVED_TEMP
import java.net.HttpURLConnection.HTTP_NOT_FOUND

@Singleton
internal class CronTabRunAction @Inject constructor(
  private val dashboardPageLayout: DashboardPageLayout,
  private val cronManager: CronManager,
) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @AdminDashboardAccess
  fun get(
    /** Cron name to run. */
    @PathParam name: String?
  ): Response<ResponseBody> {
    val cronEntries = cronManager.getCronEntries()
    return if (name == null) {
      Response(
        body = dashboardPageLayout.newBuilder().build { _, _, _ ->
          div("container mx-auto p-8") {
            AlertError("Cron name is required", label = "Try Again", onClick = "history.back(); return false;")
          }
        }.toResponseBody(),
        statusCode = HTTP_BAD_REQUEST,
      )
    } else {
      if (!cronEntries.containsKey(name)) {
        Response(
          body = dashboardPageLayout.newBuilder().build { _, _, _ ->
            div("container mx-auto p-8") {
              AlertError("Cron $name does not exist", label = "Try Again", onClick = "history.back(); return false;")
            }
          }.toResponseBody(),
          statusCode = HTTP_NOT_FOUND,
        )
      } else {
        val cron = cronEntries[name]!!
        cronManager.runCron(cron)
        val target = CronTabIndexAction.path(name)
        Response(
          body = "go to $target".toResponseBody(),
          statusCode = HTTP_MOVED_TEMP,
          headers = Headers.headersOf("Location", target),
        )
      }
    }
  }

  companion object {
    const val PATH = "/api/cron/run/{name}"
    fun path(name: String) = "/api/cron/run/$name"
  }
}
