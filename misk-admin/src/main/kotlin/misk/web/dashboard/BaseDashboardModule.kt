package misk.web.dashboard

import misk.inject.KAbstractModule
import misk.web.NetworkInterceptor
import misk.web.interceptors.WideOpenDevelopmentInterceptorFactory

/**
 * Installs base functionality for the Admin Dashboard including:
 * - multibindings for menu links and other bound in multiple places resources
 * - `admin-dashboard` tab which loads all other tabs and provides navbar, menu links, auth
 * - `@misk` packages used by Misk-Web tabs from window to provide faster tab loads
 */
class BaseDashboardModule(
  private val isDevelopment: Boolean
): KAbstractModule() {
  override fun configure() {
    // Install base dashboard support
    install(DashboardModule())

    // Adds open CORS headers in development to allow through API calls from webpack servers
    multibind<NetworkInterceptor.Factory>().to<WideOpenDevelopmentInterceptorFactory>()

    // Admin Dashboard Tab
    multibind<DashboardHomeUrl>().toInstance(
      DashboardHomeUrl<AdminDashboard>("/_admin/")
    )
    install(
      WebTabResourceModule(
        isDevelopment = isDevelopment,
        slug = "admin-dashboard",
        web_proxy_url = "http://localhost:3100/"
      )
    )
    install(
      WebTabResourceModule(
        isDevelopment = isDevelopment,
        slug = "admin-dashboard",
        web_proxy_url = "http://localhost:3100/",
        url_path_prefix = "/_admin/",
        resourcePath = "classpath:/web/_tab/admin-dashboard/"
      )
    )

    // @misk packages
    install(
      WebTabResourceModule(
        isDevelopment = isDevelopment,
        slug = "@misk",
        web_proxy_url = "http://localhost:3100/",
        url_path_prefix = "/@misk/",
        resourcePath = "classpath:/web/_tab/admin-dashboard/@misk/"
      )
    )
  }
}
