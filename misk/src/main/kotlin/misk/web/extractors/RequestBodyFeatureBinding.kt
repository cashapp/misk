package misk.web.extractors

import misk.Action
import misk.exceptions.BadRequestException
import misk.web.FeatureBinding
import misk.web.FeatureBinding.Claimer
import misk.web.FeatureBinding.Subject
import misk.web.PathPattern
import misk.web.RequestBody
import misk.web.marshal.GenericUnmarshallers
import misk.web.marshal.Unmarshaller
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KParameter

/** Binds parameters annotated [RequestBody] to the unmarshalled request body. */
internal class RequestBodyFeatureBinding(
  private val parameter: KParameter,
  private val unmarshallerFactories: List<Unmarshaller.Factory>
) : FeatureBinding {
  override fun beforeCall(subject: Subject) {
    val contentType = subject.httpCall.requestHeaders["Content-Type"]
    val mediaType = contentType?.toMediaTypeOrNull()
    val unmarshaller = mediaType?.let { type ->
      unmarshallerFactories.firstNotNullOfOrNull { it.create(type, parameter.type) }
    } ?: GenericUnmarshallers.into(parameter)

    if (unmarshaller == null) {
      if (contentType == null) {
        throw BadRequestException("Can't parse request: missing Content-Type header")
      } else {
        // If this is thrown it means an endpoint was registered with a contentType that misk
        // doesn't know how to unmarshall or marshall.
        throw IllegalStateException("Can't parse request as $contentType")
      }
    }

    val requestBody = subject.httpCall.takeRequestBody()!!
    val value = try {
      unmarshaller.unmarshal(subject.httpCall.requestHeaders, requestBody)
    } catch (e: IOException) {
      throw RequestBodyException(e)
    }
    subject.setParameter(parameter, value)
  }

  @Singleton
  class Factory @Inject internal constructor(
    @JvmSuppressWildcards private val unmarshallerFactories: List<Unmarshaller.Factory>
  ) : FeatureBinding.Factory {
    override fun create(
      action: Action,
      pathPattern: PathPattern,
      claimer: Claimer
    ): FeatureBinding? {
      val parameter = action.parameterAnnotatedOrNull<RequestBody>() ?: return null
      claimer.claimParameter(parameter)
      claimer.claimRequestBody()
      return RequestBodyFeatureBinding(parameter, unmarshallerFactories)
    }
  }
}

class RequestBodyException(
  cause: Throwable
) : IOException("unmarshalling the request body failed: $cause", cause)
