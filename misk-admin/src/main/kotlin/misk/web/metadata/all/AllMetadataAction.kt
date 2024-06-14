package misk.web.metadata.all

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.web.Get
import misk.web.PathParam
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import misk.web.metadata.Metadata

@Singleton
class AllMetadataAction @Inject constructor(
  private val allMetadata: Map<String, Metadata<Any>>
) : WebAction {
  @Get(PATH)
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AllMetadataAccess
  fun getMetadata(@PathParam id: String): Response {
    // Return all metadata if the id is "all"
    if (id == "all") return Response(data = allMetadata.mapValues { SerializableMetadata(it.value.metadata) })

    // Return metadata for the requested id
    val metadata = allMetadata.filter { it.key == id }
    return Response(data = metadata.mapValues { SerializableMetadata(it.value.metadata) })
  }

  data class Response(val data: Map<String, SerializableMetadata>)

  /** Strip out unserializable elements like Moshi adapter. */
  data class SerializableMetadata(
    val metadata: Any
  )

  companion object {
    const val PATH = "/api/{id}/metadata"
  }
}
