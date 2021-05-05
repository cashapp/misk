package misk.web.metadata.webaction

import misk.web.Get
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.dashboard.AdminDashboardProtobufDocUrlPrefix
import misk.web.jetty.WebActionsServlet
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class WebActionMetadataAction @Inject constructor() : WebAction {
  @Inject internal lateinit var servletProvider: Provider<WebActionsServlet>
  @Inject internal lateinit var protobufDocUrlPrefix: AdminDashboardProtobufDocUrlPrefix

  @Get("/api/webaction/metadata")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AdminDashboardAccess
  fun getAll(): Response {
    return Response(
      protobufDocUrlPrefix = protobufDocUrlPrefix.url,
      webActionMetadata = servletProvider.get().webActionsMetadata
    )
  }

  data class Response(
    val protobufDocUrlPrefix: String?,
    val webActionMetadata: List<WebActionMetadata>
  )
}
