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
  private val metadata: List<Metadata>
) : WebAction {
  @Get("/api/metadata/{filter}")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AllMetadataAccess
  fun getAll(
    @PathParam filter: String? = null
  ): Response {
    if (filter == null || filter == "all") {
      return Response(all = metadata)
    } else {
      return Response(all = metadata.filter { it.id == filter })
    }
  }

  data class Response(val all: List<Metadata>)
}
