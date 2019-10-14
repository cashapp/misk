package misk.web.metadata

import misk.config.ConfigMetadataAction
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.security.authz.AccessAnnotationEntry
import misk.web.DashboardTab
import misk.web.DashboardTabProvider
import misk.web.NetworkInterceptor
import misk.web.WebActionModule
import misk.web.actions.AdminDashboardTab
import misk.web.actions.DashboardMetadataAction
import misk.web.actions.ServiceMetadataAction
import misk.web.actions.WebActionMetadataAction
import misk.web.interceptors.WideOpenDevelopmentInterceptorFactory

/**
 * Installs default Admin Dashboard that runs at passed in url_path_prefix
 * Each Misk included tab in dashboard is installed with the respective:
 *  - Multibindings for API endpoints
 *  - Multibindings for Dashboard Tab registration
 *  - Dashboard Tab Module that configures location of the tab compiled web code (classpath and web proxy)
 * Non-Misk tabs can be added by binding AdminDashboardTab entries which will be displayed in the dashboard menu
 */
class AdminDashboardModule(val environment: Environment) : KAbstractModule() {
  override fun configure() {
    // Adds open CORS headers in development to allow through API calls from webpack servers
    multibind<NetworkInterceptor.Factory>().to<WideOpenDevelopmentInterceptorFactory>()

    // Initialize DashboardTab multibinding list
    newMultibinder<DashboardTab>()

    // Add metadata actions to support dashboards
    install(WebActionModule.create<DashboardMetadataAction>())
    install(WebActionModule.create<ServiceMetadataAction>())

    // Admin Dashboard Tab
    install(WebTabResourceModule(
      environment = environment,
      slug = "admin-dashboard",
      web_proxy_url = "http://localhost:3100/"
    ))
    install(WebTabResourceModule(
      environment = environment,
      slug = "admin-dashboard",
      web_proxy_url = "http://localhost:3100/",
      url_path_prefix = "/_admin/",
      resourcePath = "classpath:/web/_tab/admin-dashboard/"
    ))

    // @misk packages
    install(WebTabResourceModule(
      environment = environment,
      slug = "@misk",
      web_proxy_url = "http://localhost:3100/",
      url_path_prefix = "/@misk/",
      resourcePath = "classpath:/web/_tab/admin-dashboard/@misk/"
    ))

    // Config
    install(WebActionModule.create<ConfigMetadataAction>())
    multibind<DashboardTab>().toProvider(
      DashboardTabProvider<AdminDashboardTab, AdminDashboardAccess>(
        slug = "config",
        url_path_prefix = "/_admin/config/",
        name = "Config",
        category = "Container Admin"
      ))
    install(WebTabResourceModule(
      environment = environment,
      slug = "config",
      web_proxy_url = "http://localhost:3200/"
    ))

    // Web Actions
    install(WebActionModule.create<WebActionMetadataAction>())
    multibind<DashboardTab>().toProvider(
      DashboardTabProvider<AdminDashboardTab, AdminDashboardAccess>(
        slug = "web-actions",
        url_path_prefix = "/_admin/web-actions/",
        name = "Web Actions",
        category = "Container Admin"
      ))
    install(WebTabResourceModule(
      environment = environment,
      slug = "web-actions",
      web_proxy_url = "http://localhost:3201/"
    ))
  }
}

// Module that allows testing/development environments to bind up the admin dashboard
class AdminDashboardTestingModule(val environment: Environment) : KAbstractModule() {
  override fun configure() {
    // Set dummy values for access, these shouldn't matter,
    // as test environments should prefer to use the FakeCallerAuthenticator.
    multibind<AccessAnnotationEntry>()
      .toInstance(AccessAnnotationEntry<AdminDashboardAccess>(capabilities = listOf("admin_access")))
    install(AdminDashboardModule(environment))
  }
}
