package misk.web.dashboard

import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.metadata.webaction.WebActionMetadataAction

/**
 * Installs WebActions dashboard tab which allows introspection
 * and exercising actions from a UI form.
 *
 * This installs both versions of the WebActions tab, v1 and v2.
 */
class WebActionsDashboardTabModule(private val isDevelopment: Boolean): KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<WebActionMetadataAction>())

    // Web Actions v2
    install(DashboardModule.createMiskWebTab<AdminDashboard, AdminDashboardAccess>(
      isDevelopment = isDevelopment,
      slug = "web-actions",
      urlPathPrefix = "/_admin/web-actions/",
      developmentWebProxyUrl = "http://localhost:3201/",
      name = "Web Actions",
      category = "Container Admin"
    ))

    // Web Actions v1
    install(DashboardModule.createMiskWebTab<AdminDashboard, AdminDashboardAccess>(
      isDevelopment = isDevelopment,
      slug = "web-actions-v1",
      urlPathPrefix = "/_admin/web-actions-v1/",
      developmentWebProxyUrl = "http://localhost:3201/",
      classpathResourcePathPrefix = "classpath:/web/_tab/web-actions/",
      name = "Web Actions v1",
      category = "Container Admin"
    ))
  }
}
