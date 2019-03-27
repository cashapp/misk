package misk

import misk.web.mediatype.MediaRange
import okhttp3.MediaType
import kotlin.reflect.KFunction
import kotlin.reflect.KType

data class Action(
  val name: String,
  val function: KFunction<*>,
  val acceptedMediaRanges: List<MediaRange>,
  val responseContentType: MediaType?,
  // ParameterTypes and RequestType can differ because RequestType pertains
  // only to the type of the request body, whereas ParameterTypes includes
  // other parts of the request such as path params and query params.
  val parameterTypes: List<KType>,
  val requestType: KType?,
  val returnType: KType
)
