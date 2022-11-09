package misk.web.dashboard

import misk.inject.KAbstractModule
import misk.security.authz.AccessAnnotationEntry
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
class AdminDashboardModule(private val isDevelopment: Boolean) : KAbstractModule() {

  override fun configure() {
    install(BaseDashboardModule(isDevelopment))
    install(ConfigDashboardTabModule(isDevelopment))
    install(DatabaseDashboardTabModule(isDevelopment))
    install(WebActionsDashboardTabModule(isDevelopment))
    install(WebActionsOldDashboardTabModule(isDevelopment))

    // Default Menu
    multibind<DashboardNavbarItem>().toInstance(
      DashboardNavbarItem<AdminDashboard>(
        item = "<a href=\"/_admin/database/\">Database</a>",
        order = 100
      )
    )
    multibind<DashboardNavbarItem>().toInstance(
      DashboardNavbarItem<AdminDashboard>(
        item = "<a href=\"/_admin/web-actions/\">Web Actions</a>",
        order = 101
      )
    )
    multibind<DashboardNavbarItem>().toInstance(
      DashboardNavbarItem<AdminDashboard>(
        item = "<a href=\"/_admin/web-actions-old/\">Web Actions Old</a>",
        order = 102
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
    install(AdminDashboardModule(true))
  }
}

/** Dashboard Annotation used for all tabs bound in the Misk Admin Dashboard */
@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class AdminDashboard
