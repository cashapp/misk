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
  private val allMetadata: Map<String, Metadata>
) : WebAction {
  @Get(PATH)
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AllMetadataAccess
  fun getMetadata(@PathParam id: String): Response {
    // Return all metadata if the id is "all"
    if (id == "all") return Response(metadata = allMetadata)

    // Return metadata for the requested id
    val metadata = allMetadata.filter { it.key == id }
    return Response(metadata = metadata)
  }

  data class Response(val metadata: Map<String, Metadata>)

  companion object {
    const val PATH = "/api/{id}/metadata"
  }
}
