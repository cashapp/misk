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
  @Get("/api/{id}/metadata")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AllMetadataAccess
  fun getAll(@PathParam id: String?): Response {
    val metadata = id?.let { allMetadata.filter { it.key == id } } ?: allMetadata
    return Response(metadata = metadata)
  }

  data class Response(val metadata: Map<String, Metadata>)
}
