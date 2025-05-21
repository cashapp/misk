package misk.web.metadata.webaction

import misk.inject.KAbstractModule
import misk.web.dashboard.AdminDashboard
import misk.web.dashboard.AdminDashboardAccess
import misk.web.dashboard.DashboardModule

/**
 * Installs WebActions dashboard tab which allows introspection
 * and exercising actions from an auto-fill JSON editor.
 */
class WebActionsDashboardTabModule(private val isDevelopment: Boolean) : KAbstractModule() {
  override fun configure() {
    bind<WebActionsMetadata>().toProvider(WebActionsMetadataProvider())

    install(
      DashboardModule.createIFrameTab<AdminDashboard, AdminDashboardAccess>(
        isDevelopment = isDevelopment,
        slug = "web-actions",
        urlPathPrefix = "/_admin/web-actions/",
        iframePath = "/_tab/web-actions/index.html",
        developmentWebProxyUrl = "http://localhost:9000/",
        menuLabel = "Web Actions",
        menuCategory = "Container Admin",
      )
    )
  }
}
