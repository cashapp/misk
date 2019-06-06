package misk.web.extractors

import misk.web.PathPattern
import misk.web.HttpCall
import misk.web.RequestBody
import misk.web.actions.WebAction
import misk.web.marshal.GenericUnmarshallers
import misk.web.marshal.Unmarshaller
import okhttp3.MediaType
import java.util.regex.Matcher
import javax.inject.Inject
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation

internal class RequestBodyParameterExtractor(
  private val parameter: KParameter,
  private val unmarshallerFactories: List<Unmarshaller.Factory>
) : ParameterExtractor {
  override fun extract(
    webAction: WebAction,
    httpCall: HttpCall,
    pathMatcher: Matcher
  ): Any? {
    val mediaType = httpCall.requestHeaders["Content-Type"]?.let { MediaType.parse(it) }
    val unmarshaller = mediaType?.let { type ->
      unmarshallerFactories.map { it.create(type, parameter.type) }.filterNotNull().firstOrNull()
    } ?: GenericUnmarshallers.into(parameter)
    ?: throw IllegalArgumentException("no generic unmarshaller for ${parameter.type}")

    return unmarshaller.unmarshal(httpCall.takeRequestBody()!!)
  }

  class Factory @Inject internal constructor(
    @JvmSuppressWildcards private val unmarshallerFactories: List<Unmarshaller.Factory>
  ) : ParameterExtractor.Factory {
    override fun create(
      function: KFunction<*>,
      parameter: KParameter,
      pathPattern: PathPattern
    ): ParameterExtractor? {
      if (parameter.findAnnotation<RequestBody>() == null) return null
      return RequestBodyParameterExtractor(parameter, unmarshallerFactories)
    }
  }
}
