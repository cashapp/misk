package misk.grpc

import com.squareup.wire.MessageSink
import com.squareup.wire.MessageSource
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType

/**
 * Returns the stream element type, like `MyRequest` if this is `MessageSource<MyRequest>`.
 * Returns null if this is not a [MessageSource] or [MessageSink].
 */
internal fun KType.streamElementType(): Type? {
  // Unbox the type parameter.
  val parameterizedType = javaType as? ParameterizedType ?: return null
  if (parameterizedType.rawType != MessageSource::class.java &&
    parameterizedType.rawType != MessageSink::class.java
  ) return null
  // Remove the wildcard, like 'out MessageSource' (Kotlin) or '? super MessageSource' (Java).
  return when (val typeArgument = parameterizedType.actualTypeArguments[0]) {
    is WildcardType -> typeArgument.lowerBounds[0]
    else -> typeArgument //
  }
}

fun <T : Any> MessageSource<T>.consumeEachAndClose(block: (T) -> Unit) {
  use {
    while (true) {
      val message = read() ?: return
      block(message)
    }
  }
}
