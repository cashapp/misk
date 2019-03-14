package misk.web.actions

import misk.ApplicationInterceptor
import misk.web.DispatchMechanism
import misk.web.Get
import misk.web.NetworkInterceptor
import misk.web.PathPattern
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.formatter.ClassNameFormatter
import misk.web.jetty.WebActionsServlet
import misk.web.mediatype.MediaRange
import misk.web.mediatype.MediaTypes
import misk.web.metadata.AdminDashboardAccess
import okhttp3.MediaType
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.reflect.KType

@Singleton
class WebActionMetadataAction @Inject constructor() : WebAction {
  @Inject internal lateinit var servletProvider: Provider<WebActionsServlet>

  @Get("/api/webaction/metadata")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AdminDashboardAccess
  fun getAll(): Response {
    return Response(webActionMetadata = servletProvider.get().webActionsMetadata)
  }

  data class Response(val webActionMetadata: List<WebActionMetadata>)
}

data class WebActionMetadata(
  val name: String,
  val function: String,
  val functionAnnotations: List<String>,
  val requestMediaTypes: List<String>,
  val responseMediaType: String?,
  val parameterTypes: List<String>,
  val returnType: String,
  val pathPattern: String,
  val applicationInterceptors: List<String>,
  val networkInterceptors: List<String>,
  val dispatchMechanism: DispatchMechanism,
  val allowedServices: Set<String>,
  val allowedRoles: Set<String>
)

internal fun WebActionMetadata(
    name: String,
    function: Function<*>,
    functionAnnotations: List<Annotation>,
    acceptedMediaRanges: List<MediaRange>,
    responseContentType: MediaType?,
    parameterTypes: List<KType>,
    returnType: KType,
    pathPattern: PathPattern,
    applicationInterceptors: List<ApplicationInterceptor>,
    networkInterceptors: List<NetworkInterceptor>,
    dispatchMechanism: DispatchMechanism,
    allowedServices: Set<String>,
    allowedRoles: Set<String>
): WebActionMetadata {
  return WebActionMetadata(
      name = name,
      function = function.toString(),
      functionAnnotations = functionAnnotations.map { it.toString() },
      requestMediaTypes = acceptedMediaRanges.map { it.toString() },
      responseMediaType = responseContentType.toString(),
      parameterTypes = parameterTypes.map { it.toString() },
      returnType = returnType.toString(),
      pathPattern = pathPattern.toString(),
      applicationInterceptors = applicationInterceptors.map { ClassNameFormatter.format(it::class) },
      networkInterceptors = networkInterceptors.map { ClassNameFormatter.format(it::class) },
      dispatchMechanism = dispatchMechanism,
      allowedServices = allowedServices,
      allowedRoles = allowedRoles
  )
}