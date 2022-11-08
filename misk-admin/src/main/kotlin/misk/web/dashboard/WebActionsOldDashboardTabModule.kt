package misk.web.dashboard

import misk.inject.KAbstractModule
import misk.web.NetworkInterceptor
import misk.web.WebActionModule
import misk.web.interceptors.WideOpenDevelopmentInterceptorFactory
import misk.web.metadata.webaction.WebActionMetadataAction

/**
 * Installs WebActions dashboard tab which allows introspection
 * and exercising actions from a UI form.
 *
 * This installs the first version of the WebActions tab,
 * the [WebActionsDashboardTabModule] installs a rewrite.
 */
class WebActionsOldDashboardTabModule(private val isDevelopment: Boolean): KAbstractModule() {
  override fun configure() {
    // Web Actions Old
    multibind<DashboardTab>().toProvider(
      DashboardTabProvider<AdminDashboard, AdminDashboardAccess>(
        slug = "web-actions-old",
        url_path_prefix = "/_admin/web-actions-old/",
        name = "Web Actions Old",
        category = "Container Admin"
      )
    )
    install(
      WebTabResourceModule(
        isDevelopment = isDevelopment,
        slug = "web-actions-old",
        web_proxy_url = "http://localhost:3201/"
      )
    )
  }
}
