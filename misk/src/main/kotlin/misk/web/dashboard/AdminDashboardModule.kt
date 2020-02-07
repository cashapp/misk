package misk.web.dashboard

import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.inject.KInstallOnceModule
import misk.security.authz.AccessAnnotationEntry
import misk.web.NetworkInterceptor
import misk.web.WebActionModule
import misk.web.interceptors.WideOpenDevelopmentInterceptorFactory
import misk.web.metadata.ConfigMetadataAction
import misk.web.metadata.WebActionMetadataAction
import javax.inject.Qualifier

/**
 * Installs default Admin Dashboard that runs at multibound DashboardHomeUrl<AdminDashboard>
 *
 * Each Misk included tab in dashboard is installed with the respective:
 *  - Multibindings for API endpoints
 *  - Multibindings for Dashboard Tab registration
 *  - [WebTabResourceModule] that configures location of the tab compiled web code (classpath and web proxy)
 *
 * To add tabs to the Misk Admin Dashboard, bind the [DashboardTab] with the
 *   Dashboard Annotation [AdminDashboard]. Tabs are then included in the admin dashboard menu
 *   grouping according to the [DashboardTab].category field and sorting by [DashboardTab].name
 */
class AdminDashboardModule(val environment: Environment) : KInstallOnceModule() {
  override fun configure() {
    // Install base dashboard support
    install(DashboardModule())

    // Adds open CORS headers in development to allow through API calls from webpack servers
    multibind<NetworkInterceptor.Factory>().to<WideOpenDevelopmentInterceptorFactory>()

    // Admin Dashboard Tab
    multibind<DashboardHomeUrl>().toInstance(
      DashboardHomeUrl<AdminDashboard>("/_admin/")
    )
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
      DashboardTabProvider<AdminDashboard, AdminDashboardAccess>(
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
      DashboardTabProvider<AdminDashboard, AdminDashboardAccess>(
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
      .toInstance(
        AccessAnnotationEntry<AdminDashboardAccess>(capabilities = listOf("admin_access")))
    install(AdminDashboardModule(environment))
  }
}

/** Dashboard Annotation used for all tabs bound in the Misk Admin Dashboard */
@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class AdminDashboard
