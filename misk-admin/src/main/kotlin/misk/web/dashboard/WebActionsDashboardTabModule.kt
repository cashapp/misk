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
    // Web Actions v2
    install(WebActionModule.create<WebActionMetadataAction>())
    multibind<DashboardTab>().toProvider(
      DashboardTabProvider<AdminDashboard, AdminDashboardAccess>(
        slug = "web-actions",
        url_path_prefix = "/_admin/web-actions/",
        name = "Web Actions",
        category = "Container Admin"
      )
    )

    // Web Actions v1
    multibind<DashboardTab>().toProvider(
      DashboardTabProvider<AdminDashboard, AdminDashboardAccess>(
        slug = "web-actions",
        url_path_prefix = "/_admin/web-actions-v1/",
        name = "Web Actions V1",
        category = "Container Admin"
      )
    )

    // Both v1 and v2 live in the same tab code so only one resource module is installed
    install(
      WebTabResourceModule(
        isDevelopment = isDevelopment,
        slug = "web-actions",
        web_proxy_url = "http://localhost:3201/"
      )
    )
  }
}
