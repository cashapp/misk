package misk.web.dashboard

import misk.inject.KAbstractModule
import misk.web.NetworkInterceptor
import misk.web.WebActionModule
import misk.web.interceptors.WideOpenDevelopmentInterceptorFactory
import misk.web.metadata.DashboardMetadataAction
import misk.web.metadata.ServiceMetadataAction
import misk.web.v2.DashboardIndexAccessBlock
import misk.web.v2.DashboardIndexBlock
import misk.web.v2.DashboardV2RedirectAction

/**
 * Installs base functionality for the Admin Dashboard including:
 * - multibindings for menu links and other bound in multiple places resources
 * - `admin-dashboard` tab which loads all other tabs and provides navbar, menu links, auth
 * - `@misk` packages used by Misk-Web tabs from window to provide faster tab loads
 */
class BaseDashboardModule(private val isDevelopment: Boolean) : KAbstractModule() {
  override fun configure() {
    // Setup multibindings for dashboard related components
    newMultibinder<DashboardTab>()
    newMultibinder<DashboardHomeUrl>()
    newMultibinder<DashboardNavbarItem>()
    newMultibinder<DashboardNavbarStatus>()
    newMultibinder<DashboardTheme>()
    newMultibinder<DashboardIndexAccessBlock>()
    newMultibinder<DashboardIndexBlock>()

    // Add metadata actions to support dashboards
    install(WebActionModule.create<DashboardMetadataAction>())
    install(WebActionModule.create<ServiceMetadataAction>())

    // Redirect from old beta path /v2/_admin/ to main admin dashboard /_admin/
    install(WebActionModule.create<DashboardV2RedirectAction>())

    // Show helpful Not Found exceptions for missing Misk Web tabs in v2 dashboard
    install(WebActionModule.create<MiskWebTabIndexAction>())

    // Adds open CORS headers in development to allow through API calls from webpack servers
    multibind<NetworkInterceptor.Factory>().to<WideOpenDevelopmentInterceptorFactory>()

    // @misk packages
    install(
      WebTabResourceModule(
        isDevelopment = isDevelopment,
        slug = "@misk",
        web_proxy_url = "http://localhost:3201/",
        url_path_prefix = "/@misk/",
        // Serve the @misk dependencies from the Database tab lib directory
        resourcePath = "classpath:/web/_tab/database/@misk/",
      )
    )
  }
}
