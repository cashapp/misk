package misk.web.extractors

import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.Multimap
import misk.exceptions.requireRequest
import misk.web.FormField
import misk.web.FormValue
import misk.web.PathPattern
import misk.web.Request
import misk.web.actions.WebAction
import okio.BufferedSource
import java.net.URLDecoder
import java.util.regex.Matcher
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

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

    val kClass = parameter.type.classifier as KClass<*>
    val constructor = kClass.primaryConstructor ?: return null
    val constructorParameters = constructor.parameters.map {
      val annotation = it.findAnnotation<FormField>()
      val name = annotation?.name?.toLowerCase()
          ?: it.name?.toLowerCase()
          ?: throw IllegalStateException("cannot introspect parameter name")

      val isList = it.type.classifier?.equals(List::class) ?: false
      ConstructorParameter(
          it,
          name,
          it.isOptional,
          it.type.isMarkedNullable,
          isList,
          converterFor(if (isList) it.type.arguments.first().type!! else it.type)
      )
    }

    return object : ParameterExtractor {
      override fun extract(
        webAction: WebAction,
        request: Request,
        pathMatcher: Matcher
      ): Any? {
        val formValueMapping = parseBody(request.body)
        val parameterMap = LinkedHashMap<KParameter, Any?>()
        for (p in constructorParameters) {
          val parameterValue = formValueMapping[p.name]
          if (parameterValue == null || parameterValue.isEmpty()) {
            if (p.optional) continue
            requireRequest(p.nullable) { "${p.name} is a required value" }
          }
          parameterMap[p.kParameter] = getParameterValue(p, parameterValue)
        }

        return constructor.callBy(parameterMap)
      }
    }
  }

  private fun getParameterValue(
    p: ConstructorParameter,
    value: Collection<String>?
  ): Any? {
    if (p.isList) {
      return value?.map { p.converter?.invoke(it) }?.toList()
    }

    val first = value?.firstOrNull() ?: return null
    return p.converter?.invoke(first)
  }

  private fun parseBody(source: BufferedSource): Multimap<String, String> {
    val result = LinkedHashMultimap.create<String, String>()

    while (!source.exhausted()) {
      var keyValueEnd = source.indexOf('&'.toByte())
      if (keyValueEnd == -1L) keyValueEnd = source.buffer().size

      val keyEnd = source.indexOf('='.toByte(), 0, keyValueEnd)
      requireRequest(keyEnd != 1L) { "invalid form encoding" }

      val key = source.readUtf8(keyEnd).urlDecode().toLowerCase()
      source.readByte() // Consume '='.

      val value = source.readUtf8(keyValueEnd - keyEnd - 1).urlDecode()
      result[key].add(value)

      if (!source.exhausted()) source.readByte() // Consume '&'.
    }

    return result
  }

  private fun String.urlDecode(): String = URLDecoder.decode(this, "utf-8")

  private data class ConstructorParameter(
    val kParameter: KParameter,
    val name: String,
    val optional: Boolean,
    val nullable: Boolean,
    val isList: Boolean,
    val converter: StringConverter?
  )
}
