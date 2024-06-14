package misk.metadata.servicegraph

import jakarta.inject.Inject
import jakarta.inject.Provider
import misk.ServiceGraphBuilderMetadata
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataProvider
import misk.web.metadata.toFormattedJson
import wisp.moshi.adapter
import wisp.moshi.defaultKotlinMoshi

data class ServiceGraphMetadata(
  val builderMetadata: ServiceGraphBuilderMetadata,
) : Metadata(metadata = builderMetadata, formattedJsonString = "Service Graph Ascii Visual\n\n${builderMetadata.asciiVisual}\n\nMetadata\n\n" + defaultKotlinMoshi
  .adapter<ServiceGraphBuilderMetadata>()
  .toFormattedJson(builderMetadata))

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
