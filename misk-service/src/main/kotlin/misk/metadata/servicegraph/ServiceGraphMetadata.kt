package misk.metadata.servicegraph

import jakarta.inject.Inject
import com.google.inject.Provider
import misk.ServiceGraphBuilder
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataProvider
import misk.web.metadata.toFormattedJson
import wisp.moshi.adapter
import wisp.moshi.defaultKotlinMoshi

internal data class ServiceGraphMetadata(
  val builderMetadata: ServiceGraphBuilder.Metadata,
) : Metadata(
  metadata = builderMetadata,
  prettyPrint = "Service Graph Ascii Visual\n\n${builderMetadata.asciiVisual}\n\nMetadata\n\n" + defaultKotlinMoshi
    .adapter<ServiceGraphBuilder.Metadata>()
    .toFormattedJson(builderMetadata),
  descriptionString = "Guava service graph metadata, including a ASCII art visualization for easier debugging."
)

internal class ServiceGraphMetadataProvider : MetadataProvider<ServiceGraphMetadata> {
  override val id: String = "service-graph"

  /**
   * This must be a provider so that it is run very late in the startup lifecycle and other
   * exceptions can surface first. If exceptions come from this callsite it's confusing and often
   * not the root cause.
   */
  @Inject internal lateinit var metadataProvider: Provider<ServiceGraphBuilder.Metadata>

  override fun get() = ServiceGraphMetadata(
    builderMetadata = metadataProvider.get()
  )
}
