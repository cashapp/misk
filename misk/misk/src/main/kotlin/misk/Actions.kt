package misk

import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.mediatype.MediaRange
import okhttp3.MediaType
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.instanceParameter

fun KFunction<*>.asAction(): Action {
  val instanceParameter = this.instanceParameter
      ?: throw IllegalArgumentException("only methods may be actions")

  // Drop 'this' which is the function's first parameter.
  val actualParameters = parameters.drop(1)
  val name = instanceParameter.type.classifier?.let {
    when (it) {
      is KClass<*> -> it.simpleName
      else -> this.name
    }
  } ?: this.name

  val responseContentType = findAnnotation<ResponseContentType>()?.value?.let {
    MediaType.parse(it)
  }

  val acceptedContentTypes = findAnnotation<RequestContentType>()?.value?.flatMap {
    MediaRange.parseRanges(it)
  }?.toList() ?: listOf(MediaRange.ALL_MEDIA)

  return Action(
      name, this, acceptedContentTypes, responseContentType, actualParameters, returnType)
}
