package misk.web.metadata.all

import misk.inject.KAbstractModule
import misk.web.metadata.Metadata
import misk.web.WebActionModule
import misk.web.metadata.webaction.WebActionMetadata
import misk.web.metadata.webaction.WebActionMetadataLoader

class AllMetadataModule: KAbstractModule() {
  override fun configure() {
    // Any module can bind metadata to be exposed by the AllMetadataAction
    newMultibinder<Metadata<*>>()

    install(WebActionModule.create<AllMetadataAction>())

    // Built in metadata
    multibind<Metadata<*>>().toProvider<WebActionMetadataLoader>()
  }
}
