package misk.web.metadata.all

import misk.inject.KAbstractModule
import misk.security.authz.AccessAnnotationEntry
import misk.web.WebActionModule
import misk.web.dashboard.AdminDashboardAccess
import misk.web.metadata.Metadata
import misk.web.metadata.config.ConfigMetadataProvider
import misk.web.metadata.database.DatabaseMetadataProvider

class AllMetadataModule : KAbstractModule() {
  override fun configure() {
    // Dummy binding
    multibind<AccessAnnotationEntry>().toInstance(
      AccessAnnotationEntry<AllMetadataAccess>()
    )

    install(WebActionModule.create<AllMetadataAction>())

    // Any module can bind metadata to be exposed by the AllMetadataAction
    newMultibinder<Metadata>()

    // Built in metadata
    multibind<Metadata>().toProvider(ConfigMetadataProvider())
    multibind<Metadata>().toProvider(DatabaseMetadataProvider())
//    multibind<Metadata>().toProvider(MetadataProvider<List<WebActionMetadata>>("web-action"))
  }
}
