package misk.web.metadata

import javax.inject.Provider
import misk.inject.KAbstractModule

class MetadataModule(private val provider: Provider<Metadata>): KAbstractModule() {
  override fun configure() {
    // TODO change this to mapbinder
    multibind<Metadata>().toProvider(provider)
  }
}
