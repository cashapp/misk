package misk.web.metadata.all

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.web.Get
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import misk.web.metadata.Metadata

@Singleton
class AllMetadataAction @Inject constructor(
  private val metadata: List<Metadata>
) : WebAction {
  @Get("/api/all/metadata")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AllMetadataAccess
  fun getAll(): Response {
    return Response(all = metadata)
  }

  data class Response(val all: List<Metadata>)
}
