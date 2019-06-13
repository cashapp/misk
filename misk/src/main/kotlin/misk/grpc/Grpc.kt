package misk.grpc

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType

/**
 * Returns the channel element type, like `MyRequest` if this is `Channel<MyRequest>`. Returns null
 * if this is not a channel.
 */
internal fun KType.streamElementType(): Type? {
  val parameterizedType = javaType as? ParameterizedType ?: return null
  if (parameterizedType.rawType != GrpcReceiveChannel::class.java &&
      parameterizedType.rawType != GrpcSendChannel::class.java) return null
  return parameterizedType.actualTypeArguments[0]
}
