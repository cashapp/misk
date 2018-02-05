package misk.web.extractors

import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.jvm.javaType

/**
 * A query string parameter can be two things: a *primitive* or a List<*primitive*>
 *     This class figures out which of the two is being represented based on the KParameter
 *     used, as well as finding the KType of the primitive.
 */
internal class QueryStringParameterProcessor constructor(parameter: KParameter) {
  private val name: String? = parameter.name
  private val isList: Boolean = parameter.type.classifier?.equals(List::class) ?: false
  private val baseType: KType = if (isList) parameter.type.arguments.get(0).type!! else parameter.type

  fun extractFunctionArgumentValue(parameterStrings: List<String>): Any? {
    if (parameterStrings.isEmpty()) {
      return null
    }

    if (isList) {
      return parameterStrings.map { extractParameterValue(it) }
    }
    return extractParameterValue(parameterStrings.first())
  }

  private fun extractParameterValue(strValue: String): Any? {
    try {
      return when (baseType) {
        stringType -> strValue
        stringTypeNullable -> strValue
        intType -> strValue.toInt()
        intTypeNullable -> strValue.toInt()
        longType -> strValue.toLong()
        longTypeNullable -> strValue.toLong()
        doubleType -> strValue.toDouble()
        doubleTypeNullable -> strValue.toDouble()
        booleanType -> strValue.toBoolean()
        booleanTypeNullable -> strValue.toBoolean()
        else -> null
      }
    } catch(e: NumberFormatException) {
      throw IllegalArgumentException(
          "Invalid parameter format for $name: $strValue", e)
    }
  }

  companion object {
    // Pre-generate these specific handled KTypes so we don't need to regenerate them for every
    // query parameter for every call.
    val stringType: KType = String::class.createType(nullable = false)
    val stringTypeNullable: KType = String::class.createType(nullable = true)
    val intType: KType = Int::class.createType(nullable = false)
    val intTypeNullable: KType = Int::class.createType(nullable = true)
    val longType: KType = Long::class.createType(nullable = false)
    val longTypeNullable: KType = Long::class.createType(nullable = true)
    val doubleType: KType = Double::class.createType(nullable = false)
    val doubleTypeNullable: KType = Double::class.createType(nullable = true)
    val booleanType: KType = Boolean::class.createType(nullable = false)
    val booleanTypeNullable: KType = Boolean::class.createType(nullable = true)
  }
}
