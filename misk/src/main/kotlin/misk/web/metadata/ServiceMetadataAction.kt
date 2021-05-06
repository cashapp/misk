package misk.web.metadata

import misk.config.AppName
import misk.environment.Environment
import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service Metadata used for front end dashboards including App Name and Misk.Environment
 */
@Singleton
class ServiceMetadataAction @Inject constructor(
  private val optionalBinder: OptionalBinder,
) : WebAction {
  @Get("/api/service/metadata")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Unauthenticated
  fun getAll(): Response {
    return Response(
      serviceMetadata = optionalBinder.serviceMetadata
    )
  }

  data class ServiceMetadata(
    val app_name: String,
    val environment: String,
  )

  data class Response(val serviceMetadata: ServiceMetadata)

  /**
   * https://github.com/google/guice/wiki/FrequentlyAskedQuestions#how-can-i-inject-optional-parameters-into-a-constructor
   */
  @Singleton
  class OptionalBinder @Inject constructor(@AppName val appName: String) {
    @com.google.inject.Inject(optional = true)
    var serviceMetadata: ServiceMetadata =
      ServiceMetadata(appName, Environment.fromEnvironmentVariable().name)
  }
}
