package misk.metadata.servicegraph

import com.google.inject.Provider
import jakarta.inject.Inject
import misk.ServiceGraphBuilderMetadata
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataProvider
import misk.web.metadata.toFormattedJson
import wisp.moshi.ProviderJsonAdapterFactory
import misk.moshi.adapter
import wisp.moshi.buildMoshi

data class ServiceGraphMetadata(
  val builderMetadata: ServiceGraphBuilderMetadata,
) : Metadata(
  metadata = builderMetadata,
  prettyPrint = "Service Graph Ascii Visual\n\n${builderMetadata.asciiVisual}\n\nMetadata\n\n" + buildMoshi(listOf(ProviderJsonAdapterFactory()))
    .adapter<ServiceGraphBuilderMetadata>()
    .toFormattedJson(builderMetadata),
  descriptionString = "Guava service graph metadata, including a ASCII art visualization for easier debugging."
) {
  /** Only evaluate this lazily to avoid initiating service startup through the [CoordinatedService::toMetadataProvider()] method. */
  val graphVisual by lazy { generateGraphVisual() }

  data class GraphPairs(
    val source: String,
    val target: String,
  )

  private fun extractType(input: String): String {
    // Find the start index of the type
    val typeStartIndex = input.indexOf("type=")
    if (typeStartIndex == -1) return input

    // Find the end index of the type
    val typeEndIndex = input.indexOf(",", typeStartIndex)
    if (typeEndIndex == -1) return input

    // Extract and return the type substring
    return input.substring(typeStartIndex + "type=".length, typeEndIndex)
  }

  private fun generateGraphVisual(): MutableList<GraphPairs> {
    val output = mutableListOf<GraphPairs>()

    builderMetadata.serviceMap.forEach { (key, value) ->
      val dependencies = value.get().dependencies

      dependencies.forEach { target ->
        output.add(GraphPairs(extractType(key), extractType(target)))
      }
    }

    return output
  }
}

internal class ServiceGraphMetadataProvider : MetadataProvider<ServiceGraphMetadata> {
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
