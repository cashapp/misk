package misk.web.dashboard

import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.dashboard.AdminDashboardModule.Companion.DEFAULT_TAB_CATEGORY
import misk.web.metadata.webaction.WebActionMetadataAction

/**
 * Installs WebActions dashboard tab which allows introspection
 * and exercising actions from a UI form.
 *
 * This installs both versions of the WebActions tab, v1 and v2.
 */
class WebActionsDashboardTabModule(private val isDevelopment: Boolean): KAbstractModule() {
  override fun configure() {
    // Web Actions v2
    install(WebActionModule.create<WebActionMetadataAction>())

    // Web Actions v2
    install(DashboardModule.createMiskWebTab<AdminDashboard, AdminDashboardAccess>(
      isDevelopment = isDevelopment,
      slug = "web-actions",
      urlPathPrefix = "/_admin/web-actions/",
      developmentWebProxyUrl = "http://localhost:3201/",
      menuLabel = "Web Actions",
      menuCategory = DEFAULT_TAB_CATEGORY
    ))

    // Web Actions v1
    install(DashboardModule.createMiskWebTab<AdminDashboard, AdminDashboardAccess>(
      isDevelopment = isDevelopment,
      slug = "web-actions-v1",
      urlPathPrefix = "/_admin/web-actions-v1/",
      developmentWebProxyUrl = "http://localhost:3201/",
      classpathResourcePathPrefix = "classpath:/web/_tab/web-actions/",
      menuLabel = "Web Actions v1",
      menuCategory = DEFAULT_TAB_CATEGORY
    ))
  }
}
