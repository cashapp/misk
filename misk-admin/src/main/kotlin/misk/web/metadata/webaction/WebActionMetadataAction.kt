package misk.web.metadata.webaction

import misk.web.Get
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class WebActionMetadataAction @Inject constructor(
  val webActionMetadataProvider: WebActionMetadataProvider
) : WebAction {
  @Get("/api/webaction/metadata")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AdminDashboardAccess
  fun getAll(): Response {
    return Response(
      webActionMetadata = webActionMetadataProvider.get().webActions
    )
  }

  data class Response(val webActionMetadata: List<WebActionMetadata>)
}
