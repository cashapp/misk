package misk.web.dashboard

import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.security.authz.AccessAnnotationEntry
import misk.web.NetworkInterceptor
import misk.web.WebActionModule
import misk.web.interceptors.WideOpenDevelopmentInterceptorFactory
import misk.web.metadata.database.DatabaseQueryMetadata
import misk.web.metadata.database.DatabaseQueryMetadataAction
import misk.web.metadata.database.NoAdminDashboardDatabaseAccess
import misk.web.metadata.webaction.WebActionMetadataAction
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
class AdminDashboardModule(
  private val isDevelopment: Boolean,
  private val dashboardProtobufDocUrlPrefix: String? = null
) : KAbstractModule() {

  @Deprecated("Environment is deprecated")
  constructor(env: Environment) : this(env == Environment.TESTING || env == Environment.DEVELOPMENT)

  override fun configure() {
    // Install base dashboard support
    install(DashboardModule())

    bind<AdminDashboardProtobufDocUrlPrefix>()
      .toInstance(AdminDashboardProtobufDocUrlPrefix(dashboardProtobufDocUrlPrefix))

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

    // Database Query
    newMultibinder<DatabaseQueryMetadata>()
    install(WebActionModule.create<DatabaseQueryMetadataAction>())
    multibind<DashboardTab>().toProvider(
      DashboardTabProvider<AdminDashboard, AdminDashboardAccess>(
        slug = "database",
        url_path_prefix = "/_admin/database/",
        name = "Database",
        category = "Container Admin"
      )
    )
    install(
      WebTabResourceModule(
        isDevelopment = isDevelopment,
        slug = "database",
        web_proxy_url = "http://localhost:3202/"
      )
    )
    // Default access that doesn't allow any queries for unconfigured DbEntities
    multibind<AccessAnnotationEntry>().toInstance(
      AccessAnnotationEntry<NoAdminDashboardDatabaseAccess>(
        capabilities = listOf("no_admin_dashboard_database_access")
      )
    )
    multibind<DashboardNavbarItem>().toInstance(
      DashboardNavbarItem<AdminDashboard>(
        item = "<a href=\"/_admin/database/\">Database</a>",
        order = 100
      )
    )

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
    multibind<DashboardNavbarItem>().toInstance(
      DashboardNavbarItem<AdminDashboard>(
        item = "<a href=\"/_admin/web-actions/\">Web Actions</a>",
        order = 101
      )
    )

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
    multibind<DashboardNavbarItem>().toInstance(
      DashboardNavbarItem<AdminDashboard>(
        item = "<a href=\"/_admin/web-actions-old/\">Web Actions Old</a>",
        order = 101
      )
    )
  }
}

// Module that allows testing/development environments to bind up the admin dashboard
class AdminDashboardTestingModule : KAbstractModule() {
  override fun configure() {
    // Set dummy values for access, these shouldn't matter,
    // as test environments should prefer to use the FakeCallerAuthenticator.
    multibind<AccessAnnotationEntry>().toInstance(
      AccessAnnotationEntry<AdminDashboardAccess>(
        capabilities = listOf(
          "admin_access", "admin_console", "users"
        )
      )
    )
    install(
      AdminDashboardModule(
        isDevelopment = true,
        dashboardProtobufDocUrlPrefix = "https://example.com/"
      )
    )
  }
}

/** Dashboard Annotation used for all tabs bound in the Misk Admin Dashboard */
@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class AdminDashboard
