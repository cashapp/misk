package misk.web.actions

import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.jetty.WebActionsServlet
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class WebActionMetadataAction : WebAction {
  @Inject internal lateinit var servletProvider: Provider<WebActionsServlet>

  @Get("/api/webactionmetadata")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Unauthenticated // TODO(adrw) add AccessAnnotation for AdminTab
  fun getAll(): Response {
    return Response(webActionMetadata = servletProvider.get().webActionsMetadata)
  }

  data class Response(val webActionMetadata: List<WebActionMetadata>)
}