package misk.web.extractors

import misk.web.FormValue
import misk.web.HttpCall
import misk.web.PathPattern
import misk.web.actions.WebAction
import java.util.regex.Matcher
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation

object FormValueParameterExtractorFactory : ParameterExtractor.Factory {
  /**
   * Creates a [ParameterExtractor] that extracts the form value parameter with the same name as
   * [parameter] and returns it as whatever type is specified in [parameter]. Returns null if the
   * parameter doesn't occur in the request, or can't be parsed into the correct type.
   */
  override fun create(
    function: KFunction<*>,
    parameter: KParameter,
    pathPattern: PathPattern
  ): ParameterExtractor? {
    parameter.findAnnotation<FormValue>() ?: return null
    if (parameter.type.classifier !is KClass<*>) return null

    val kClass: KClass<*> = parameter.type.classifier as KClass<*>
    val formClass = FormAdapter.create(kClass) ?: return null

    return object : ParameterExtractor {
      override fun extract(
        webAction: WebAction,
        httpCall: HttpCall,
        pathMatcher: Matcher
      ): Any? {
        val formData = FormData.decode(httpCall.takeRequestBody()!!)
        return formClass.fromFormData(formData)
      }
    }
  }
}
