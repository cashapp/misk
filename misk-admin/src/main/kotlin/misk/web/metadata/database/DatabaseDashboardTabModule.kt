package misk.web.metadata.database

import misk.inject.KAbstractModule
import misk.jdbc.AdminDatabaseEntry
import misk.security.authz.AccessAnnotationEntry
import misk.web.WebActionModule
import misk.web.dashboard.AdminDashboard
import misk.web.dashboard.AdminDashboardAccess
import misk.web.dashboard.DashboardModule

/** Installs Database dashboard tab which allows querying the database from a UI form. */
class DatabaseDashboardTabModule(private val isDevelopment: Boolean) : KAbstractModule() {
  override fun configure() {
    // Database Query
    newMultibinder<DatabaseQueryMetadata>()
    install(WebActionModule.create<DatabaseQueryMetadataAction>())

    // Admin Database Entries (auto-populated by JdbcModule for each DataSource)
    newMultibinder<AdminDatabaseEntry>()

    // New Database Tab
    install(WebActionModule.create<DatabaseTabIndexAction>())
    install(WebActionModule.create<DatabaseQueryAction>())
    install(
      DashboardModule.createHotwireTab<AdminDashboard, AdminDashboardAccess>(
        slug = "database-beta",
        urlPathPrefix = DatabaseTabIndexAction.PATH,
        menuCategory = "Container Admin",
        menuLabel = "Database Beta",
      )
    )

    // Old Database Tab
    install(
      DashboardModule.createMiskWebTab<AdminDashboard, AdminDashboardAccess>(
        isDevelopment = isDevelopment,
        slug = "database",
        urlPathPrefix = "/_admin/database/",
        developmentWebProxyUrl = "http://localhost:3202/",
        menuLabel = "Database",
        menuCategory = "Container Admin",
      )
    )

    // Default access that doesn't allow any queries for unconfigured DbEntities
    multibind<AccessAnnotationEntry>()
      .toInstance(
        AccessAnnotationEntry<NoAdminDashboardDatabaseAccess>(
          capabilities = listOf("no_admin_dashboard_database_access")
        )
      )
  }
}
