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
  fun getAll(@PathParam id: String? = null): Response {
    // Return all metadata if the id is "all"
    if (id == "all") return Response(all = allMetadata)

    // Return metadata for the requested id
    return Response(all = allMetadata.filter { it.key == id })
  }

  data class Response(val all: Map<String, Metadata>)

  companion object {
    const val PATH = "/api/{id}/metadata"
  }
}
