package misk.web.metadata.webaction

import misk.ApplicationInterceptor
import misk.web.DispatchMechanism
import misk.web.MiskWebFormBuilder
import misk.web.NetworkInterceptor
import misk.web.PathPattern
import misk.web.formatter.ClassNameFormatter
import misk.web.mediatype.MediaRange
import okhttp3.MediaType
import kotlin.reflect.KType

/** Metadata front end model for Web Action Misk-Web Tab */
data class WebActionMetadata(
  val name: String,
  val function: String,
  val functionAnnotations: List<String>,
  val requestMediaTypes: List<String>,
  val responseMediaType: String?,
  val parameterTypes: List<String>,
  val requestType: String?,
  val returnType: String,
  val types: Map<String, MiskWebFormBuilder.Type>,
  val pathPattern: String,
  val applicationInterceptors: List<String>,
  val networkInterceptors: List<String>,
  val httpMethod: String,
  val allowedServices: Set<String>,
  val allowedCapabilities: Set<String>
) {
  constructor(
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
  ) : this(
      name = name,
      function = function.toString(),
      functionAnnotations = functionAnnotations.map { it.toString() },
      requestMediaTypes = acceptedMediaRanges.map { it.toString() },
      responseMediaType = responseContentType.toString(),
      parameterTypes = parameterTypes.map { it.toString() },
      requestType = requestType.toString(),
      returnType = returnType.toString(),
      types = MiskWebFormBuilder().calculateTypes(requestType),
      pathPattern = pathPattern.toString(),
      applicationInterceptors = applicationInterceptors.map {
        ClassNameFormatter.format(it::class)
      },
      networkInterceptors = networkInterceptors.map { ClassNameFormatter.format(it::class) },
      httpMethod = dispatchMechanism.method,
      allowedServices = allowedServices,
      allowedCapabilities = allowedCapabilities
  )
}
