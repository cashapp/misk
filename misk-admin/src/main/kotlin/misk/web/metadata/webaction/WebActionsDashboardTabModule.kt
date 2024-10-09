package misk.web.metadata.webaction

import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.dashboard.AdminDashboard
import misk.web.dashboard.AdminDashboardAccess
import misk.web.dashboard.DashboardModule

/**
 * Installs WebActions dashboard tab which allows introspection
 * and exercising actions from a UI form.
 *
 * This installs both versions of the WebActions tab, v1 and v2.
 */
class WebActionsDashboardTabModule(private val isDevelopment: Boolean): KAbstractModule() {
  override fun configure() {
    // Web Actions v2
    bind<WebActionsMetadata>().toProvider(WebActionsMetadataProvider())
    install(WebActionModule.create<WebActionMetadataAction>())

    // Web Actions v4
    install(
      DashboardModule.createIFrameTab<AdminDashboard, AdminDashboardAccess>(
        // TODO get development proxy working for IFrame tabs
//        isDevelopment = isDevelopment,
        slug = "web-actions-v4",
        urlPathPrefix = "/_admin/web-actions-v4/",
        iframePath = "/_tab/web-actions-v4/index.html",
//        developmentWebProxyUrl = "http://localhost:3210/",
        menuLabel = "Web Actions Beta",
        menuCategory = "Container Admin",
      )
    )


    // Web Actions v2
    install(
      DashboardModule.createMiskWebTab<AdminDashboard, AdminDashboardAccess>(
        isDevelopment = isDevelopment,
        slug = "web-actions",
        urlPathPrefix = "/_admin/web-actions/",
        developmentWebProxyUrl = "http://localhost:3201/",
        menuLabel = "Web Actions",
        menuCategory = "Container Admin"
      )
    )

    // Web Actions v1
    install(
      DashboardModule.createMiskWebTab<AdminDashboard, AdminDashboardAccess>(
        isDevelopment = isDevelopment,
        slug = "web-actions-v1",
        urlPathPrefix = "/_admin/web-actions-v1/",
        developmentWebProxyUrl = "http://localhost:3201/",
        classpathResourcePathPrefix = "classpath:/web/_tab/web-actions/",
        menuLabel = "Web Actions v1",
        menuCategory = "Container Admin"
      )
    )
  }
}
