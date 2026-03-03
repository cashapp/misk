package misk.web.extractors

import misk.exceptions.requireRequest
import misk.web.FormField
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

/**
 * Binds a Kotlin class to a [FormData]. All fields are supplied to the class constructor which must
 * be either named directly or annotated [FormField].
 */
internal class FormAdapter<T : Any> private constructor(
  private val constructor: KFunction<T>,
  private val fields: List<Field>
) {
  internal fun fromFormData(formData: FormData): T {
    val parameterMap = mutableMapOf<KParameter, Any?>()
    for (field in fields) {
      val values = formData[field.name]
      if (values.isEmpty() && field.optional) continue
      requireRequest(values.isNotEmpty() || field.nullable) {
        "${field.name} is a required value"
      }
      parameterMap[field.kParameter] = field.valuesToParameter(values)
    }
    return constructor.callBy(parameterMap)
  }

  /** Binds a form field to a constructor parameter. */
  private data class Field(
    val kParameter: KParameter,
    val name: String,
    val optional: Boolean,
    val nullable: Boolean,
    val isList: Boolean,
    val converter: StringConverter?
  ) {
    fun valuesToParameter(values: Collection<String>): Any? {
      if (isList) {
        return values.map { converter?.invoke(it) }.toList()
      } else {
        val first = values.firstOrNull() ?: return null
        return converter?.invoke(first)
      }
    }
  }

  companion object {
    /** Returns an adapter for [kClass], or null if it cannot be adapted. */
    fun <T : Any> create(kClass: KClass<T>): FormAdapter<T>? {
      val constructor = kClass.primaryConstructor ?: return null
      val fields = constructor.parameters.map { it.toField() }
      return FormAdapter(constructor, fields)
    }

    private fun KParameter.toField(): Field {
      val annotation = findAnnotation<FormField>()
      val name = annotation?.name?.toLowerCase()
          ?: name?.toLowerCase()
          ?: throw IllegalStateException("cannot introspect parameter name")

      // TODO(jwilson): explode if no converter is found. As is we just replace values with nulls.
      val isList = type.classifier?.equals(List::class) ?: false
      return Field(
          this,
          name,
          isOptional,
          type.isMarkedNullable,
          isList,
          converterFor(if (isList) type.arguments.first().type!! else type)
      )
    }
  }
}
