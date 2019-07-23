package misk.grpc

import com.squareup.wire.ProtoAdapter
import misk.Action
import misk.web.DispatchMechanism
import misk.web.FeatureBinding
import misk.web.PathPattern
import misk.web.mediatype.MediaTypes
import java.lang.reflect.Type
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.jvm.javaType

internal class GrpcResponseFeatureBinding(
  private val adapter: ProtoAdapter<Any>,
  private val streaming: Boolean
) : FeatureBinding {
  override fun bind(subject: FeatureBinding.Subject) {
    subject.httpCall.setResponseHeader("grpc-encoding", "identity")
    subject.httpCall.setResponseHeader("grpc-accept-encoding", "gzip")

    subject.httpCall.requireTrailers()
    // TODO(jwilson): permit non-0 GRPC statuses.
    subject.httpCall.setResponseTrailer("grpc-status", "0")
    // TODO(jwilson): permit non-identity GRPC encoding.

    val responseBody = subject.takeResponseBody()
    val messageSink = GrpcMessageSink(responseBody, adapter)

    subject.httpCall.setResponseHeader("Content-Type",
        MediaTypes.APPLICATION_GRPC_MEDIA_TYPE.toString())

    if (streaming) {
      // It's a streaming response, give the call a SendChannel to write to.
      subject.setParameter(1, messageSink)
    } else {
      // It's a single response, write the return value out.
      val returnValue = subject.takeReturnValue()!!
      messageSink.write(returnValue)
      messageSink.close()
    }
  }

  @Singleton
  class Factory @Inject internal constructor() : FeatureBinding.Factory {
    override fun create(
      action: Action,
      pathPattern: PathPattern,
      claimer: FeatureBinding.Claimer
    ): FeatureBinding? {
      if (action.dispatchMechanism != DispatchMechanism.GRPC) return null

      require(action.parameters.size in 1..2) {
        "@Grpc functions must have either 1 or 2 parameters: $action"
      }

      // Bind the response body to either parameter 1 (streaming) or the return type (single).
      claimer.claimResponseBody()
      if (action.parameters.size == 2) {
        claimer.claimParameter(1)
        val responseType: Type = action.parameters[1].type.streamElementType()
            ?: error("@Grpc function's second parameter should be a MessageSource: $action")
        @Suppress("UNCHECKED_CAST") // Assume it's a proto type.
        return GrpcResponseFeatureBinding(
            adapter = ProtoAdapter.get(responseType as Class<Any>),
            streaming = true
        )
      } else {
        claimer.claimReturnValue()
        @Suppress("UNCHECKED_CAST") // Assume it's a proto type.
        return GrpcResponseFeatureBinding(
            adapter = ProtoAdapter.get(action.returnType.javaType as Class<Any>),
            streaming = false
        )
      }
    }
  }
}