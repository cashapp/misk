package misk

import misk.web.DispatchMechanism
import misk.web.RequestBody
import misk.web.actions.javaMethod
import misk.web.mediatype.MediaRange
import okhttp3.MediaType
import java.lang.reflect.Method
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation

/**
 * Adapts a function so that it can be called by the framework.
 *
 * This adapts the parameters and return value as HTTP content (like the request and response
 * bodies, path parameters, and query parameters).
 *
 * This aggregates annotations from overridden functions on inherited interfaces and superclasses.
 * For example, putting `@RequestBody` on an interface function parameter is as good as putting it
 * on the implementing function's parameter. If both a supertype function and a subtype function
 * have the same annotation, the subtype's annotation takes precedence.
 *
 */
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

  val functionAsJavaMethod: Method?
    get() = function.javaMethod

  override fun toString() = function.toString()
}
