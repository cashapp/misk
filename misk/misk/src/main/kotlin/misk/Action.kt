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
  val parameterTypes: List<KType>,
  val returnType: KType
)
