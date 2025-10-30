package misk.web.dashboard

import jakarta.inject.Qualifier
import misk.inject.KAbstractModule
import misk.security.authz.AccessAnnotationEntry
import misk.web.dev.DevModule
import misk.web.metadata.config.ConfigDashboardTabModule
import misk.web.metadata.config.ConfigMetadataAction
import misk.web.metadata.database.DatabaseDashboardTabModule
import misk.web.metadata.guice.GuiceDashboardTabModule
import misk.web.metadata.servicegraph.ServiceGraphDashboardTabModule
import misk.web.metadata.webaction.WebActionsDashboardTabModule
import misk.web.v2.NavbarModule

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
class AdminDashboardModule @JvmOverloads constructor(
  private val isDevelopment: Boolean,
  private val configTabMode: ConfigMetadataAction.ConfigTabMode = ConfigMetadataAction.ConfigTabMode.SAFE,
) : KAbstractModule() {
  override fun configure() {
    // Base setup
    install(BaseDashboardModule(isDevelopment))
    install(NavbarModule())

    if (System.getProperty("misk.dev.running") == "true") {
      install(DevModule())
    }

    // Default container admin tabs
    install(ConfigDashboardTabModule(isDevelopment, configTabMode))
    install(DatabaseDashboardTabModule(isDevelopment))
    install(GuiceDashboardTabModule())
    install(ServiceGraphDashboardTabModule())
    install(WebActionsDashboardTabModule(isDevelopment))
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
        configTabMode = ConfigMetadataAction.ConfigTabMode.UNSAFE_LEAK_MISK_SECRETS,
      )
    )
  }
}

/** Dashboard Annotation used for all tabs bound in the Misk Admin Dashboard */
@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class AdminDashboard
