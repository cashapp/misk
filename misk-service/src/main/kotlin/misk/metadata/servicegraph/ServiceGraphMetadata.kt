package misk.metadata.servicegraph

import jakarta.inject.Inject
import jakarta.inject.Provider
import misk.ServiceGraphBuilderMetadata
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataProvider

data class ServiceGraphMetadata(
  val builderMetadata: ServiceGraphBuilderMetadata,
) : Metadata(metadata = builderMetadata)

class ServiceGraphMetadataProvider : MetadataProvider<ServiceGraphMetadata> {
  override val id: String = "service-graph"

  /**
   * This must be a provider so that it is run very late in the startup lifecycle and other
   * exceptions can surface first. If exceptions come from this callsite it's confusing and often
   * not the root cause.
   */
  @Inject internal lateinit var metadataProvider: Provider<ServiceGraphBuilderMetadata>

  override fun get() = ServiceGraphMetadata(
    builderMetadata = metadataProvider.get()
  )
}
