package misk.web.actions

import misk.environment.Environment
import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Singleton

/**
 * metadata to build navbar of admin dashboard
 */

@Singleton
class ServiceMetadataAction : WebAction {
  @Inject lateinit var serviceMetadata: ServiceMetadata

  @Get("/api/service/metadata")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Unauthenticated
  fun getAll(): Response {
    return Response(serviceMetadata = serviceMetadata)
  }
  data class Response(val serviceMetadata: ServiceMetadata)
}

data class ServiceMetadata(
  val app_name: String,
  val environment: Environment,
  val admin_dashboard_url: String
)