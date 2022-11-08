package misk.web.dashboard

import misk.inject.KAbstractModule
import misk.security.authz.AccessAnnotationEntry
import misk.web.WebActionModule
import misk.web.metadata.database.DatabaseQueryMetadata
import misk.web.metadata.database.DatabaseQueryMetadataAction
import misk.web.metadata.database.NoAdminDashboardDatabaseAccess

/**
 * Installs Database dashboard tab which allows querying the database from a UI form.
 */
class DatabaseDashboardTabModule(private val isDevelopment: Boolean): KAbstractModule() {
  override fun configure() {
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
  }
}
