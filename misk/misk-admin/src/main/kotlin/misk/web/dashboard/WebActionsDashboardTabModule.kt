package misk.web.dashboard

import misk.inject.KAbstractModule
import misk.web.NetworkInterceptor
import misk.web.WebActionModule
import misk.web.interceptors.WideOpenDevelopmentInterceptorFactory
import misk.web.metadata.webaction.WebActionMetadataAction

/**
 * Installs WebActions dashboard tab which allows introspection
 * and exercising actions from a UI form.
 */
class WebActionsDashboardTabModule(private val isDevelopment: Boolean): KAbstractModule() {
  override fun configure() {
    // Web Actions
    install(WebActionModule.create<WebActionMetadataAction>())
    multibind<DashboardTab>().toProvider(
      DashboardTabProvider<AdminDashboard, AdminDashboardAccess>(
        slug = "web-actions",
        url_path_prefix = "/_admin/web-actions/",
        name = "Web Actions",
        category = "Container Admin"
      )
    )
    install(
      WebTabResourceModule(
        isDevelopment = isDevelopment,
        slug = "web-actions",
        web_proxy_url = "http://localhost:3201/"
      )
    )
  }
}
