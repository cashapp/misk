package misk.web.actions

import misk.ApplicationInterceptor
import misk.web.DispatchMechanism
import misk.web.NetworkInterceptor
import misk.web.PathPattern
import misk.web.mediatype.MediaRange
import okhttp3.MediaType
import kotlin.reflect.KType

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
  val dispatchMechanism: DispatchMechanism
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
  dispatchMechanism: DispatchMechanism
) : WebActionMetadata {
  return WebActionMetadata(
      name = name,
      function = function.toString(),
      functionAnnotations = functionAnnotations.map { it.toString() },
      requestMediaTypes = acceptedMediaRanges.map { it.toString() },
      responseMediaType = responseContentType.toString(),
      parameterTypes = parameterTypes.map { it.toString() },
      returnType = returnType.toString(),
      pathPattern = pathPattern.toString(),
      applicationInterceptors = applicationInterceptors.map { it::class.qualifiedName.toString() },
      networkInterceptors = networkInterceptors.map { it::class.qualifiedName.toString() },
      dispatchMechanism = dispatchMechanism
  )
}