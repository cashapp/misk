package misk.grpc

import com.squareup.wire.ProtoAdapter
import misk.Action
import misk.web.DispatchMechanism
import misk.web.FeatureBinding
import misk.web.FeatureBinding.Claimer
import misk.web.FeatureBinding.Subject
import misk.web.PathPattern
import misk.web.mediatype.MediaTypes
import java.lang.reflect.Type
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.jvm.javaType

internal class GrpcFeatureBinding(
  private val requestAdapter: ProtoAdapter<Any>,
  private val responseAdapter: ProtoAdapter<Any>,
  private val streamingRequest: Boolean,
  private val streamingResponse: Boolean
) : FeatureBinding {
  override fun beforeCall(subject: Subject) {
    val requestBody = subject.takeRequestBody()
    val messageSource = GrpcMessageSource(
      requestBody, requestAdapter,
      subject.httpCall.requestHeaders["grpc-encoding"]
    )

    if (streamingRequest) {
      subject.setParameter(0, messageSource)
    } else {
      val request = messageSource.read()!!
      subject.setParameter(0, request)
    }

    if (streamingResponse) {
      val responseBody = subject.takeResponseBody()
      val messageSink = GrpcMessageSink(responseBody, responseAdapter, grpcEncoding = "identity")

      // It's a streaming response, give the call a SendChannel to write to.
      subject.setParameter(1, messageSink)
      setResponseHeaders(subject)
    }
  }

  override fun afterCall(subject: Subject) {
    check(!streamingResponse)

    setResponseHeaders(subject)

    val responseBody = subject.takeResponseBody()
    val messageSink = GrpcMessageSink(responseBody, responseAdapter, grpcEncoding = "identity")

    // It's a single response, write the return value out.
    val returnValue = subject.takeReturnValue()!!
    messageSink.write(returnValue)
    messageSink.close()
  }

  private fun setResponseHeaders(subject: Subject) {
    subject.httpCall.requireTrailers()

    // TODO(jwilson): permit non-identity GRPC encoding.
    subject.httpCall.setResponseHeader("grpc-encoding", "identity")
    subject.httpCall.setResponseHeader("grpc-accept-encoding", "gzip")
    subject.httpCall.setResponseHeader("Content-Type", MediaTypes.APPLICATION_GRPC)

    // TODO(jwilson): permit non-0 GRPC statuses.
    subject.httpCall.setResponseTrailer("grpc-status", "0")
  }

  @Singleton
  class Factory @Inject internal constructor() : FeatureBinding.Factory {
    override fun create(
      action: Action,
      pathPattern: PathPattern,
      claimer: Claimer
    ): FeatureBinding? {
      if (action.dispatchMechanism != DispatchMechanism.GRPC) return null

      require(action.parameters.size in 1..2) {
        "@Grpc functions must have either 1 or 2 parameters: $action"
      }

      claimer.claimParameter(0)
      claimer.claimRequestBody()
      val streamingRequestType = action.parameters[0].type.streamElementType()

      // Bind the response body to either parameter 1 (streaming) or the return type (single).
      claimer.claimResponseBody()
      val streamingResponse = action.parameters.size == 2
      val responseAdapter = if (action.parameters.size == 2) {
        claimer.claimParameter(1)
        val responseType: Type = action.parameters[1].type.streamElementType()
          ?: error("@Grpc function's second parameter should be a MessageSource: $action")
        @Suppress("UNCHECKED_CAST") // Assume it's a proto type.
        ProtoAdapter.get(responseType as Class<Any>)
      } else {
        claimer.claimReturnValue()
        @Suppress("UNCHECKED_CAST") // Assume it's a proto type.
        ProtoAdapter.get(action.returnType.javaType as Class<Any>)
      }

      return if (streamingRequestType != null) {
        @Suppress("UNCHECKED_CAST") // Assume it's a proto type.
        GrpcFeatureBinding(
          requestAdapter = ProtoAdapter.get(streamingRequestType as Class<Any>),
          responseAdapter = responseAdapter,
          streamingRequest = true,
          streamingResponse = streamingResponse
        )
      } else {
        @Suppress("UNCHECKED_CAST") // Assume it's a proto type.
        GrpcFeatureBinding(
          requestAdapter = ProtoAdapter.get(action.parameters[0].type.javaType as Class<Any>),
          responseAdapter = responseAdapter,
          streamingRequest = false,
          streamingResponse = streamingResponse
        )
      }
    }
  }
}
