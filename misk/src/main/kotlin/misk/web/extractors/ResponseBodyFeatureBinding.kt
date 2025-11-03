package misk.web.extractors

import com.squareup.wire.MessageSink
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.RENDEZVOUS
import kotlinx.coroutines.channels.SendChannel
import misk.Action
import misk.web.DispatchMechanism
import misk.web.FeatureBinding
import misk.web.FeatureBinding.Claimer
import misk.web.FeatureBinding.Subject
import misk.web.Grpc
import misk.web.PathPattern
import misk.web.ResponseSink
import misk.web.ResponseSinkChannel
import misk.web.actions.WebSocketListener
import misk.web.interceptors.ResponseBodyMarshallerFactory
import misk.web.marshal.Marshaller
import misk.web.mediatype.MediaTypes
import misk.web.sse.ServerSentEvent
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.typeOf

internal class ResponseBodyFeatureBinding(
  private val responseBodyMarshaller: Marshaller<Any>,
  private val streamingResponseParameter: Int? = null,
  private val isSuspend: Boolean,
) : FeatureBinding {
  override fun beforeCall(subject: Subject) {
    if (streamingResponseParameter == null) return

    val responseBody = subject.takeResponseBody()
    val eventSink = ResponseSink(
      sink = responseBody,
      httpCall = subject.httpCall,
      responseBodyMarshaller = responseBodyMarshaller,
    )
    val param = if (isSuspend) {
      ResponseSinkChannel(
        channel = Channel(
          capacity = RENDEZVOUS,
          onBufferOverflow = BufferOverflow.SUSPEND,
        ),
        sink = eventSink,
      )
    } else {
      eventSink
    }

    subject.setParameter(streamingResponseParameter, param)
    with(subject.httpCall) {
      setResponseHeader("Content-Type", MediaTypes.SERVER_EVENT_STREAM)
      setResponseHeader("Cache-Control", "no-cache")
      setResponseHeader("Connection", "keep-alive")
      setResponseHeader("X-Accel-Buffering", "no")
    }
  }

  override fun afterCall(subject: Subject) {
    if (streamingResponseParameter != null) return

    val returnValue = subject.takeReturnValue()!!
    val httpCall = subject.httpCall
    subject.takeResponseBody().use { sink ->
      val contentType = responseBodyMarshaller.contentType()
      if (httpCall.responseHeaders["Content-Type"] == null && contentType != null) {
        httpCall.setResponseHeader("Content-Type", contentType.toString())
      }
      val responseBody = responseBodyMarshaller.responseBody(returnValue, httpCall)
      responseBody.writeTo(sink)
    }
  }

  @Singleton
  class Factory @Inject internal constructor(
    private val responseBodyMarshallerFactory: ResponseBodyMarshallerFactory
  ) : FeatureBinding.Factory {
    override fun create(
      action: Action,
      pathPattern: PathPattern,
      claimer: Claimer,
      stringConverterFactories: List<StringConverter.Factory>
    ): FeatureBinding? {
      if (action.dispatchMechanism == DispatchMechanism.GRPC && action.function.findAnnotation<Grpc>() == null) return null
      if (action.responseContentType != MediaTypes.SERVER_EVENT_STREAM_TYPE && action.returnType.classifier == Unit::class) return null
      if (action.returnType.classifier == WebSocketListener::class) return null

      claimer.claimResponseBody()

      // If the action response type is 'text/event-stream' and the return type is Unit, then we expect a streaming
      // response parameter of type ResponseSink or SendChannel.
      return if (action.responseContentType == MediaTypes.SERVER_EVENT_STREAM_TYPE) {
        check(action.returnType.classifier == Unit::class) {
          "Actions using SSE should not have a return type"
        }
        val streamingResponseParam = action.parameters.singleOrNull { param ->
          param.type.classifier.let {
            it == MessageSink::class || it == SendChannel::class
          }
        }
          ?: error("Actions using SSE need a single ResponseSink(blocking) or SendChannel(suspending) parameter: $action")

        check(streamingResponseParam.type.arguments.singleOrNull()?.type == typeOf<ServerSentEvent>()) {
          "SSE streaming parameters need to be of ServerSentEvent: $action"
        }

        val streamingResponseParameterIndex = action.parameters.indexOf(streamingResponseParam)
        claimer.claimParameter(streamingResponseParameterIndex)

        val responseBodyMarshaller = responseBodyMarshallerFactory.create(
          responseMediaType = MediaTypes.SERVER_EVENT_STREAM_TYPE,
          type = typeOf<ServerSentEvent>(),
        )

        ResponseBodyFeatureBinding(
          responseBodyMarshaller = responseBodyMarshaller,
          streamingResponseParameter = streamingResponseParameterIndex,
          isSuspend = action.function.isSuspend,
        )
      } else {
        val responseBodyMarshaller = responseBodyMarshallerFactory.create(action)
        claimer.claimReturnValue()
        ResponseBodyFeatureBinding(
          responseBodyMarshaller = responseBodyMarshaller,
          streamingResponseParameter = null,
          isSuspend = action.function.isSuspend,
        )
      }
    }
  }
}
