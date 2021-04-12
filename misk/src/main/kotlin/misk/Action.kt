package misk

import misk.web.DispatchMechanism
import misk.web.RequestBody
import misk.web.mediatype.MediaRange
import okhttp3.MediaType
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation

data class Action(
  val name: String,
  val function: KFunction<*>,
  val acceptedMediaRanges: List<MediaRange>,
  val responseContentType: MediaType?,
  val parameters: List<KParameter>,
  val returnType: KType,
  val dispatchMechanism: DispatchMechanism
) {
  /**
   * ParameterTypes and RequestType can differ because RequestType pertains
   * only to the type of the request body, whereas ParameterTypes includes
   * other parts of the request such as path params and query params.
   */
  val parameterTypes: List<KType>
    get() = parameters.map { it.type }

  val annotatedParameters: List<String>
    get() = parameters.map {
      if (it.annotations.size > 0) {
        "${it.annotations.joinToString(",")} ${it.name}: ${it.type}"
      } else {
        "${it.name}: ${it.type}"
      }
    }

  val requestType: KType?
    get() {
      if (dispatchMechanism == DispatchMechanism.GRPC) {
        // TODO: support gRPC streaming on the web dashboard.
        return if (parameters.size == 1) parameters[0].type else null
      } else {
        return parameters.filter { it.annotations.any { it is RequestBody } }
          .map { it.type }
          .firstOrNull()
      }
    }

  internal inline fun <reified T : Annotation> parameterAnnotatedOrNull(): KParameter? {
    return parameters.firstOrNull { it.findAnnotation<T>() != null }
  }

  fun hasReturnValue() = returnType.classifier != Unit::class

  override fun toString() = function.toString()
}
