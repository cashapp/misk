package misk.web.metadata.all

import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.metadata.Metadata
import misk.web.metadata.config.ConfigMetadataProvider
import misk.web.metadata.database.DatabaseHibernateMetadataProvider
import misk.web.metadata.webaction.WebActionsMetadataProvider

/**
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

    // Any module can bind metadata to be exposed by the AllMetadataAction
    newMultibinder<Metadata>()

    // Built in metadata
    multibind<Metadata>().toProvider(ConfigMetadataProvider())
    multibind<Metadata>().toProvider(DatabaseHibernateMetadataProvider())
    multibind<Metadata>().toProvider(WebActionsMetadataProvider())
  }
}
