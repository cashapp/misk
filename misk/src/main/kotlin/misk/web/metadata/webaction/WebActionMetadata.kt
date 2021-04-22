package misk.web.metadata.webaction

import misk.ApplicationInterceptor
import misk.web.DispatchMechanism
import misk.web.MiskWebFormBuilder
import misk.web.NetworkInterceptor
import misk.web.PathPattern
import misk.web.formatter.ClassNameFormatter
import misk.web.mediatype.MediaRange
import okhttp3.MediaType
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

/** Metadata front end model for Web Action Misk-Web Tab */
data class WebActionMetadata(
  val name: String,
  val function: String,
  val packageName: String?,
  val description: String?,
  val functionAnnotations: List<String>,
  val requestMediaTypes: List<String>,
  val responseMediaType: String?,
  val parameterTypes: List<String>,
  val parameters: List<ParameterMetaData>,
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
    function: KFunction<*>,
    functionAnnotations: List<Annotation>,
    description: String?,
    acceptedMediaRanges: List<MediaRange>,
    responseContentType: MediaType?,
    parameterTypes: List<KType>,
    parameters: List<KParameter>,
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
    packageName = function.javaMethod?.declaringClass?.packageName,
    functionAnnotations = functionAnnotations.map { it.toString() },
    description = description,
    requestMediaTypes = acceptedMediaRanges.map { it.toString() },
    responseMediaType = responseContentType.toString(),
    parameterTypes = parameterTypes.map { it.toString() },
    parameters = parameters.map { parameter ->
      ParameterMetaData(
        name = parameter.name,
        annotations = parameter.annotations.map { annotation -> annotation.toString() },
        type = parameter.type.toString()
      )
    },
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

  data class ParameterMetaData(
    val name: String?,
    val annotations: List<String>,
    val type: String
  )
}
