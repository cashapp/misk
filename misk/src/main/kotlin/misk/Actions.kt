package misk

import misk.web.DispatchMechanism
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.mediatype.MediaRange
import misk.web.mediatype.MediaTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.instanceParameter

fun KFunction<*>.asAction(
  dispatchMechanism: DispatchMechanism
): Action {
  val instanceParameter = instanceParameter
      ?: throw IllegalArgumentException("only methods may be actions")

  // Drop 'this' which is the function's first parameter.
  val actualParameters = parameters.drop(1)
  val actionName = instanceParameter.type.classifier?.let {
    when (it) {
      is KClass<*> -> it.simpleName
      else -> name
    }
  } ?: name

  val acceptedMediaRange =
      when (dispatchMechanism) {
        DispatchMechanism.GRPC -> {
          require(findAnnotation<RequestContentType>() == null) {
            "@Grpc cannot be used with @RequestContentType on $this"
          }
          listOf(MediaRange.parse(MediaTypes.APPLICATION_GRPC))
        }
        else -> findAnnotation<RequestContentType>()?.value?.flatMap {
          MediaRange.parseRanges(it)
        }?.toList() ?: listOf(MediaRange.ALL_MEDIA)
      }

  val responseContentType =
      when (dispatchMechanism) {
        DispatchMechanism.GRPC -> {
          require(findAnnotation<ResponseContentType>() == null) {
            "@Grpc cannot be used with @ResponseContentType on $this"
          }
          MediaTypes.APPLICATION_GRPC_MEDIA_TYPE
        }
        else -> findAnnotation<ResponseContentType>()?.value?.let {
          it.toMediaTypeOrNull()
        }
      }

  return Action(
      name = actionName,
      function = this,
      acceptedMediaRanges = acceptedMediaRange,
      responseContentType = responseContentType,
      parameters = actualParameters,
      returnType = returnType,
      dispatchMechanism = dispatchMechanism
  )
}
