package misk.web.metadata

import misk.ApplicationInterceptor
import misk.web.DispatchMechanism
import misk.web.Get
import misk.web.ConcurrencyLimitsOptOut
import misk.web.NetworkInterceptor
import misk.web.PathPattern
import misk.web.RequestContentType
import misk.web.RequestTypes
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.formatter.ClassNameFormatter
import misk.web.jetty.WebActionsServlet
import misk.web.mediatype.MediaRange
import misk.web.mediatype.MediaTypes
import okhttp3.MediaType
import org.eclipse.jetty.http.HttpMethod
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.reflect.KType

@Singleton
class WebActionMetadataAction @Inject constructor() : WebAction {
  @Inject internal lateinit var servletProvider: Provider<WebActionsServlet>

  @Get("/api/webaction/metadata")
  @ConcurrencyLimitsOptOut // TODO: Remove after 2020-08-01 (or use @AvailableWhenDegraded).
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AdminDashboardAccess
  fun getAll(): Response {
    return Response(
      webActionMetadata = servletProvider.get().webActionsMetadata)
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
  val requestType: String?,
  val returnType: String,
  val types: Map<String, Type>,
  val pathPattern: String,
  val applicationInterceptors: List<String>,
  val networkInterceptors: List<String>,
  val httpMethod: String,
  val allowedServices: Set<String>,
  val allowedCapabilities: Set<String>
)

data class Type(val fields: List<Field>)
data class Field(val name: String, val type: String, val repeated: Boolean)

internal fun WebActionMetadata(
  name: String,
  function: Function<*>,
  functionAnnotations: List<Annotation>,
  acceptedMediaRanges: List<MediaRange>,
  responseContentType: MediaType?,
  parameterTypes: List<KType>,
  requestType: KType?,
  returnType: KType,
  pathPattern: PathPattern,
  applicationInterceptors: List<ApplicationInterceptor>,
  networkInterceptors: List<NetworkInterceptor>,
  dispatchMechanism: DispatchMechanism,
  allowedServices: Set<String>,
  allowedCapabilities: Set<String>
): WebActionMetadata {
  return WebActionMetadata(
    name = name,
    function = function.toString(),
    functionAnnotations = functionAnnotations.map { it.toString() },
    requestMediaTypes = acceptedMediaRanges.map { it.toString() },
    responseMediaType = responseContentType.toString(),
    parameterTypes = parameterTypes.map { it.toString() },
    requestType = requestType.toString(),
    returnType = returnType.toString(),
    types = RequestTypes().calculateTypes(requestType),
    pathPattern = pathPattern.toString(),
    applicationInterceptors = applicationInterceptors.map { ClassNameFormatter.format(it::class) },
    networkInterceptors = networkInterceptors.map { ClassNameFormatter.format(it::class) },
    httpMethod = dispatchMechanism.method,
    allowedServices = allowedServices,
    allowedCapabilities = allowedCapabilities
  )
}
