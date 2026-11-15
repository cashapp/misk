package misk.web.metadata.database

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.html.div
import kotlinx.html.h1
import misk.tailwind.components.AlertInfoHighlight
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes
import misk.web.v2.DashboardPageLayout

@Singleton
internal class DatabaseTabIndexAction @Inject constructor(private val dashboardPageLayout: DashboardPageLayout) :
  WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @AdminDashboardAccess
  fun get(): String =
    dashboardPageLayout.newBuilder().build { _, _, _ ->
      div("container mx-auto p-8") {
        h1("text-3xl font-bold mb-4") { +"""Database Beta""" }
        AlertInfoHighlight("Execute SQL queries against your database.", "Old Tab", "/_admin/database/")
      }
    }

  companion object {
    const val PATH = "/_admin/database-beta/"
  }
}
