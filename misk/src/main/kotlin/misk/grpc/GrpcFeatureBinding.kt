package misk.grpc

import com.squareup.wire.ProtoAdapter
import com.squareup.wire.WireRpc
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.RENDEZVOUS
import misk.Action
import misk.web.DispatchMechanism
import misk.web.FeatureBinding
import misk.web.FeatureBinding.Claimer
import misk.web.FeatureBinding.Subject
import misk.web.PathPattern
import misk.web.WebConfig
import misk.web.extractors.StringConverter
import misk.web.mediatype.MediaTypes
import java.lang.reflect.Type
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.full.findAnnotation

internal class GrpcFeatureBinding(
  private val requestAdapter: ProtoAdapter<Any>,
  private val responseAdapter: ProtoAdapter<Any>,
  private val streamingRequest: Boolean,
  private val streamingResponse: Boolean,
  private val isSuspend: Boolean,
  private val grpcMessageSourceChannelContext: CoroutineContext,
  private val grpcEncoding: String,
  private val minMessageToCompress: Long,
) : FeatureBinding {

  override fun beforeCall(subject: Subject) {
    val requestBody = subject.takeRequestBody()
    val messageSource = GrpcMessageSource(
      requestBody, requestAdapter,
      subject.httpCall.requestHeaders["grpc-encoding"]
    )
    // TODO: support the "grpc-accept-encoding" header

    if (streamingRequest) {
      val param: Any = if (isSuspend) {
        GrpcMessageSourceChannel(
          channel = Channel(
            capacity = RENDEZVOUS,
            onBufferOverflow = BufferOverflow.SUSPEND,
          ),
          source = messageSource,
          coroutineContext = grpcMessageSourceChannelContext,
        )
      } else {
        messageSource
      }
      subject.setParameter(0, param)
    } else {
      val request = messageSource.read()!!
      subject.setParameter(0, request)
    }

    if (streamingResponse) {
      val responseBody = subject.takeResponseBody()
      val messageSink = GrpcMessageSink(
        sink = responseBody,
        minMessageToCompress = minMessageToCompress,
        messageAdapter = responseAdapter,
        grpcEncoding = grpcEncoding,
      )
      val param: Any = if (isSuspend) {
        GrpcMessageSinkChannel(
          channel = Channel(
            capacity = RENDEZVOUS,
            onBufferOverflow = BufferOverflow.SUSPEND,
          ),
          sink = messageSink,
        )
      } else {
        messageSink
      }

      // It's a streaming response, give the call a SendChannel to write to.
      subject.setParameter(1, param)
      setResponseHeaders(subject)
    }
  }

  override fun afterCall(subject: Subject) {
    check(!streamingResponse)

    setResponseHeaders(subject)

    val responseBody = subject.takeResponseBody()
    val messageSink = GrpcMessageSink(
      sink = responseBody,
      minMessageToCompress = minMessageToCompress,
      messageAdapter = responseAdapter,
      grpcEncoding = grpcEncoding,
    )

    // It's a single response, write the return value out.
    val returnValue = subject.takeReturnValue()!!
    messageSink.write(returnValue)
    messageSink.close()
  }

  private fun setResponseHeaders(subject: Subject) {
    subject.httpCall.requireTrailers()

    subject.httpCall.setResponseHeader("grpc-encoding", grpcEncoding)
    subject.httpCall.setResponseHeader("grpc-accept-encoding", "gzip")
    subject.httpCall.setResponseHeader("Content-Type", MediaTypes.APPLICATION_GRPC)

    // TODO(jwilson): permit non-0 GRPC statuses.
    subject.httpCall.setResponseTrailer("grpc-status", "0")
  }

  @Singleton
  class Factory @Inject internal constructor(
    webConfig: WebConfig
  ) : FeatureBinding.Factory {

    /**
     * This dispatcher is sized to the jetty thread pool size to make sure that
     * no requests that are currently scheduled on a jetty thread are ever blocked
     * from reading a streaming request.
     */
    private val grpcMessageSourceChannelDispatcher =
      Dispatchers.IO.limitedParallelism(
        parallelism = webConfig.jetty_max_thread_pool_size,
        name = "GrpcMessageSourceChannel.bridgeFromSource"
      )

    private val grpcEncoding = if (webConfig.grpcGzip) "gzip" else "identity"

    private val minMessageToCompress = webConfig.minGzipSize.toLong()

    override fun create(
      action: Action,
      pathPattern: PathPattern,
      claimer: Claimer,
      stringConverterFactories: List<StringConverter.Factory>
    ): FeatureBinding? {
      if (action.dispatchMechanism != DispatchMechanism.GRPC) return null

      require(action.parameters.size in 1..2) {
        "@Grpc functions must have either 1 or 2 parameters: $action"
      }

      val wireAnnotation = action.function.findAnnotation<WireRpc>() ?: return null

      claimer.claimParameter(0)
      claimer.claimRequestBody()
      val streamingRequestType = action.parameters[0].type.streamElementType()

      // Bind the response body to either parameter 1 (streaming) or the return type (single).
      claimer.claimResponseBody()
      val streamingResponse = action.parameters.size == 2
      val responseAdapter = if (action.parameters.size == 2) {
        claimer.claimParameter(1)
        val responseType: Type = action.parameters[1].type.streamElementType()
          ?: error("@Grpc function's second parameter should be a MessageSink(blocking) or SendChannel(suspending): $action")
        @Suppress("UNCHECKED_CAST") // Assume it's a proto type.
        ProtoAdapter.get(responseType as Class<Any>)
      } else {
        claimer.claimReturnValue()
        @Suppress("UNCHECKED_CAST") // Assume it's a proto type.
        ProtoAdapter.get(wireAnnotation.responseAdapter) as ProtoAdapter<Any>
      }
      val isSuspend = action.function.isSuspend

      return if (streamingRequestType != null) {
        @Suppress("UNCHECKED_CAST") // Assume it's a proto type.
        GrpcFeatureBinding(
          requestAdapter = ProtoAdapter.get(streamingRequestType as Class<Any>),
          responseAdapter = responseAdapter,
          streamingRequest = true,
          streamingResponse = streamingResponse,
          isSuspend = isSuspend,
          grpcMessageSourceChannelContext = grpcMessageSourceChannelDispatcher,
          grpcEncoding = grpcEncoding,
          minMessageToCompress = minMessageToCompress,
        )
      } else {
        @Suppress("UNCHECKED_CAST") // Assume it's a proto type.
        GrpcFeatureBinding(
          requestAdapter = ProtoAdapter.get(wireAnnotation.requestAdapter) as ProtoAdapter<Any>,
          responseAdapter = responseAdapter,
          streamingRequest = false,
          streamingResponse = streamingResponse,
          isSuspend = isSuspend,
          grpcMessageSourceChannelContext = grpcMessageSourceChannelDispatcher,
          grpcEncoding = grpcEncoding,
          minMessageToCompress = minMessageToCompress,
        )
      }
    }
  }
}
