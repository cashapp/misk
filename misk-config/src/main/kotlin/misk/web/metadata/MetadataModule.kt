package misk.web.metadata

import misk.inject.KAbstractModule

/** Installs a new Metadata type with associated provider to expose in [AllMetadataAction]. */
class MetadataModule<T : Metadata>(private val provider: MetadataProvider<T>) : KAbstractModule() {
  override fun configure() {
    val binder = newMapBinder<String, Metadata>()
    binder.addBinding(provider.id).toProvider(provider)
  }
}
