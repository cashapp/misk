package misk.web.metadata

import misk.config.ConfigAdminAction
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.security.authz.AccessAnnotationEntry
import misk.web.DashboardTab
import misk.web.NetworkInterceptor
import misk.web.WebActionModule
import misk.web.actions.AdminDashboardTab
import misk.web.actions.AdminDashboardTabAction
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

    // Loader
    install(WebActionModule.create<AdminDashboardTabAction>())
    install(WebActionModule.create<ServiceMetadataAction>())
    install(WebTabResourceModule(
        environment = environment,
        slug = "loader",
        web_proxy_url = "http://localhost:3100/"
    ))
    install(WebTabResourceModule(
        environment = environment,
        slug = "loader",
        web_proxy_url = "http://localhost:3100/",
        url_path_prefix = "/_admin/",
        resourcePath = "classpath:/web/_tab/loader/"
    ))

    // @misk packages
    install(WebTabResourceModule(
        environment = environment,
        slug = "@misk",
        web_proxy_url = "http://localhost:9100/",
        url_path_prefix = "/@misk/",
        resourcePath = "classpath:/web/_tab/loader/@misk/"
    ))

    // Config
    install(WebActionModule.create<ConfigAdminAction>())
    multibind<DashboardTab, AdminDashboardTab>().toInstance(DashboardTab(
        name = "Config",
        slug = "config",
        url_path_prefix = "/_admin/config/",
        category = "Container Admin"
    ))
    install(WebTabResourceModule(
        environment = environment,
        slug = "config",
        web_proxy_url = "http://localhost:3200/"
    ))

    // Web Actions
    install(WebActionModule.create<WebActionMetadataAction>())
    multibind<DashboardTab, AdminDashboardTab>().toInstance(DashboardTab(
        name = "Web Actions",
        slug = "web-actions",
        url_path_prefix = "/_admin/web-actions/"
    ))
    install(WebTabResourceModule(
        environment = environment,
        slug = "web-actions",
        web_proxy_url = "http://localhost:3201/"
    ))

    // True for testing Misk Menu with populated tabs and categories, tabs are not functional
    if (environment == Environment.DEVELOPMENT || environment == Environment.TESTING) {
      multibind<DashboardTab, AdminDashboardTab>().toInstance(DashboardTab(
          name = "gRPC client",
          slug = "a",
          url_path_prefix = "/_admin/a/",
          category = "Container Admin"
      ))
      multibind<DashboardTab, AdminDashboardTab>().toInstance(DashboardTab(
          name = "gRPC server",
          slug = "a",
          url_path_prefix = "/_admin/a/",
          category = "Container Admin"
      ))
      multibind<DashboardTab, AdminDashboardTab>().toInstance(DashboardTab(
          name = "Threads",
          slug = "a",
          url_path_prefix = "/_admin/a/",
          category = "Container Admin"
      ))
      multibind<DashboardTab, AdminDashboardTab>().toInstance(DashboardTab(
          name = "Guice",
          slug = "a",
          url_path_prefix = "/_admin/a/",
          category = "Container Admin"
      ))
      multibind<DashboardTab, AdminDashboardTab>().toInstance(DashboardTab(
          name = "Connections",
          slug = "a",
          url_path_prefix = "/_admin/a/",
          category = "Container Admin"
      ))
    }
  }
}

// Module that allows testing/development environments to bind up the admin dashboard
class AdminDashboardTestingModule(val environment: Environment): KAbstractModule() {
  override fun configure() {
    install(AdminDashboardModule(environment))
    multibind<AccessAnnotationEntry>()
        // Set dummy values for access, these shouldn't matter,
        // as test environments should prefer to use the FakeCallerAuthenticator.
        .toInstance(AccessAnnotationEntry<AdminDashboardAccess>(roles = listOf("admin_access")))
  }
}