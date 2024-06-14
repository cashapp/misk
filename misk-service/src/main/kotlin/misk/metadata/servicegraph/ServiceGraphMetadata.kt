package misk.metadata.servicegraph

import com.squareup.moshi.JsonAdapter
import jakarta.inject.Inject
import jakarta.inject.Provider
import misk.ServiceGraphBuilderMetadata
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataProvider
import wisp.moshi.adapter
import wisp.moshi.defaultKotlinMoshi

data class ServiceGraphMetadata(
  override val metadata: ServiceGraphBuilderMetadata,
  override val adapter: JsonAdapter<ServiceGraphBuilderMetadata> = defaultKotlinMoshi.adapter<ServiceGraphBuilderMetadata>(),
) : Metadata<ServiceGraphBuilderMetadata>

class ServiceGraphMetadataProvider : MetadataProvider<ServiceGraphBuilderMetadata, ServiceGraphMetadata> {
  override val id: String = "service-graph"

  /**
   * This must be a provider so that it is run very late in the startup lifecycle and other
   * exceptions can surface first. If exceptions come from this callsite it's confusing and often
   * not the root cause.
   */
  @Inject internal lateinit var metadataProvider: Provider<ServiceGraphBuilderMetadata>

  override fun get() = ServiceGraphMetadata(
    metadata = metadataProvider.get()
  )
}
