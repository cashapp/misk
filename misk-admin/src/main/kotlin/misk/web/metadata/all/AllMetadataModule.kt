package misk.web.metadata.all

import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.metadata.MetadataProvider
import misk.web.metadata.Metadata
import misk.web.metadata.config.ConfigMetadata
import misk.web.metadata.database.DatabaseQueryMetadata
import misk.web.metadata.webaction.WebActionMetadata

class AllMetadataModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<AllMetadataAction>())

    // Any module can bind metadata to be exposed by the AllMetadataAction
    multibind<Metadata>()

    // Built in metadata
    multibind<Metadata>().toProvider(MetadataProvider<ConfigMetadata>("config"))
    multibind<Metadata>().toProvider(MetadataProvider<List<DatabaseQueryMetadata>>("database-hibernate"))
    multibind<Metadata>().toProvider(MetadataProvider<List<WebActionMetadata>>("web-action"))
  }
}
