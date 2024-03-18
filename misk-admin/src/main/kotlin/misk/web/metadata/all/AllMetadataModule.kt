package misk.web.metadata.all

import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.metadata.MetadataProvider
import misk.web.metadata.Metadata
import misk.web.metadata.webaction.WebActionMetadata

class AllMetadataModule: KAbstractModule() {
  override fun configure() {
    // Any module can bind metadata to be exposed by the AllMetadataAction
    multibind<Metadata>()

    install(WebActionModule.create<AllMetadataAction>())

   // Built in metadata
    multibind<Metadata>().toProvider(MetadataProvider<List<WebActionMetadata>>("web-action"))

  }
}
