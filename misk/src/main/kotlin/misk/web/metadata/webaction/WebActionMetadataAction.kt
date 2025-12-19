package misk.web.metadata.webaction

import com.google.inject.Provider
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.web.Get
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes

@Singleton
class WebActionMetadataAction @Inject constructor() : WebAction {
  @Inject private lateinit var provider: Provider<WebActionsMetadata>

  @Get("/api/v1/webaction/metadata")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AdminDashboardAccess
  fun getAll(): Response {
    return Response(webActionMetadata = provider.get().webActions)
  }

  data class Response(val webActionMetadata: List<WebActionMetadata>)
}
