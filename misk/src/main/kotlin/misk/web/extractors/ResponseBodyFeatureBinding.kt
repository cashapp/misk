package misk.web.extractors

import misk.Action
import misk.web.DispatchMechanism
import misk.web.FeatureBinding
import misk.web.FeatureBinding.Claimer
import misk.web.FeatureBinding.Subject
import misk.web.Grpc
import misk.web.PathPattern
import misk.web.actions.WebSocketListener
import misk.web.interceptors.ResponseBodyMarshallerFactory
import misk.web.marshal.Marshaller
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.reflect.full.findAnnotation

internal class ResponseBodyFeatureBinding(
  private val responseBodyMarshaller: Marshaller<Any>
) : FeatureBinding {
  override fun afterCall(subject: Subject) {
    val returnValue = subject.takeReturnValue()!!
    val httpCall = subject.httpCall
    subject.takeResponseBody().use { sink ->
      val contentType = responseBodyMarshaller.contentType()
      if (httpCall.responseHeaders.get("Content-Type") == null && contentType != null) {
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
      if (action.returnType.classifier == Unit::class) return null
      if (action.returnType.classifier == WebSocketListener::class) return null

      val responseBodyMarshaller = responseBodyMarshallerFactory.create(action)
      claimer.claimReturnValue()
      claimer.claimResponseBody()
      return ResponseBodyFeatureBinding(responseBodyMarshaller)
    }
  }
}
