package misk.web.dashboard

import misk.inject.KAbstractModule
import misk.security.authz.AccessAnnotationEntry
import misk.web.v2.BaseDashboardV2Module
import misk.web.metadata.config.ConfigMetadataAction
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
  private val configTabMode: ConfigMetadataAction.ConfigTabMode = ConfigMetadataAction.ConfigTabMode.SAFE,
) : KAbstractModule() {

  override fun configure() {
    // v1 Dashboard
    install(BaseDashboardModule(isDevelopment))

    // Default container admin tabs
    install(ConfigDashboardTabModule(isDevelopment, configTabMode))
    install(DatabaseDashboardTabModule(isDevelopment))
    install(WebActionsDashboardTabModule(isDevelopment))

    // v2 Dashboard
    install(BaseDashboardV2Module())

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

    // Provide maximum information in development as real secrets won't be present
    install(
      AdminDashboardModule(
        isDevelopment = true,
        configTabMode = ConfigMetadataAction.ConfigTabMode.UNSAFE_LEAK_MISK_SECRETS
      )
    )
  }
}

/** Dashboard Annotation used for all tabs bound in the Misk Admin Dashboard */
@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class AdminDashboard
