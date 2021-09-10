package misk.web.metadata.webaction

import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebActionMetadataAction @Inject constructor(
  val webActionMetadataList: WebActionMetadataList
) : WebAction {
  @Get("/api/webaction/metadata")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Unauthenticated
  fun getAll(): Response {
    return Response(
      webActionMetadata = webActionMetadataList.get()
    )
  }

  data class Response(val webActionMetadata: List<WebActionMetadata>)
}
