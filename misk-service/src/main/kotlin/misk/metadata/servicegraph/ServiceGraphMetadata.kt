package misk.metadata.servicegraph

import jakarta.inject.Inject
import misk.ServiceGraphBuilder
import misk.ServiceGraphBuilderMetadata
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataProvider

data class ServiceGraphMetadata(
  val builderMetadata: ServiceGraphBuilderMetadata,
) : Metadata(metadata = builderMetadata)

class ServiceGraphMetadataProvider : MetadataProvider<ServiceGraphMetadata> {
  override val id: String = "service-graph"

  @Inject internal lateinit var builder: ServiceGraphBuilder

  override fun get() = ServiceGraphMetadata(
    builderMetadata = builder.toMetadata()
  )
}
