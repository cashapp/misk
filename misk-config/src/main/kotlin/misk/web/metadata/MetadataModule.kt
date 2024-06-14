package misk.web.metadata

import misk.inject.KAbstractModule

/** Installs a new Metadata type with associated provider to expose in [AllMetadataAction]. */
class MetadataModule<ST: Any, T: Metadata<ST>>(private val provider: MetadataProvider<ST, T>): KAbstractModule() {
  override fun configure() {
    val binder = newMapBinder<String, Metadata<ST>>()
    binder.addBinding(provider.id).toProvider(provider)
  }
}
