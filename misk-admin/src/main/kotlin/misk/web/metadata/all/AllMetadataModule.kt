package misk.web.metadata.all

import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.dashboard.AdminDashboard
import misk.web.dashboard.AdminDashboardAccess
import misk.web.dashboard.DashboardModule
import misk.web.metadata.MetadataModule
import misk.web.metadata.config.ConfigMetadataProvider
import misk.web.metadata.database.DatabaseHibernateMetadataProvider
import misk.web.metadata.jvm.JvmMetadataProvider
import misk.web.metadata.webaction.WebActionsMetadataProvider

/**
 * This exposes extensive metadata about your Misk application via API and admin dashboard.
 *
 * To install and use, ensure you also add an AccessAnnotationEntry to grant endpoint access.
 *
 * ```kotlin
 * multibind<AccessAnnotationEntry>().toInstance(
 *   AccessAnnotationEntry<AllMetadataAccess>(
 *     services = listOf("security-service")
 *   )
 * )
 */
class AllMetadataModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<AllMetadataAction>())

    // Built in metadata
    install(MetadataModule(ConfigMetadataProvider()))
    install(MetadataModule(DatabaseHibernateMetadataProvider()))
    install(MetadataModule(JvmMetadataProvider()))
    install(MetadataModule(WebActionsMetadataProvider()))

    // Install dashbaord tab
    install(WebActionModule.create<MetadataTabIndexAction>())
    install(
      DashboardModule.createHotwireTab<AdminDashboard, AdminDashboardAccess>(
        slug = "metadata",
        urlPathPrefix = MetadataTabIndexAction.PATH,
        menuLabel = "Metadata",
        menuCategory = "Container Admin",
      )
    )
  }
}
