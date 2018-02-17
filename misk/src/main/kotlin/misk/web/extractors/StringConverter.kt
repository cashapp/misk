package misk.web.extractors

import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.jvm.javaType

// Pre-generate these specific handled KTypes so we don't need to regenerate them for every
// query parameter for every call.
private val stringType: KType = String::class.createType(nullable = false)
private val stringTypeNullable: KType = String::class.createType(nullable = true)
private val intType: KType = Int::class.createType(nullable = false)
private val intTypeNullable: KType = Int::class.createType(nullable = true)
private val longType: KType = Long::class.createType(nullable = false)
private val longTypeNullable: KType = Long::class.createType(nullable = true)
private val doubleType: KType = Double::class.createType(nullable = false)
private val doubleTypeNullable: KType = Double::class.createType(nullable = true)
private val booleanType: KType = Boolean::class.createType(nullable = false)
private val booleanTypeNullable: KType = Boolean::class.createType(nullable = true)

internal typealias StringConverter = (String) -> Any?

internal fun converterFor(type: KType): StringConverter? = when (type) {
  stringType -> { it -> it }
  stringTypeNullable -> { it -> it }
  intType -> numberConversionWrapper { it.toInt() }
  intTypeNullable -> numberConversionWrapper { it.toInt() }
  longType -> numberConversionWrapper { it.toLong() }
  longTypeNullable -> numberConversionWrapper { it.toLong() }
  doubleType -> numberConversionWrapper { it.toDouble() }
  doubleTypeNullable -> numberConversionWrapper { it.toDouble() }
  booleanType -> numberConversionWrapper { it.toBoolean() }
  booleanTypeNullable -> numberConversionWrapper { it.toBoolean() }
  else -> createFromValueOf(type)
}

private fun numberConversionWrapper(wrappedFunc: StringConverter): StringConverter {
  return {
    try {
      wrappedFunc(it)
    } catch (e: NumberFormatException) {
      throw IllegalArgumentException("Invalid parameter format: $it", e)
    }
  }
}

private fun createFromValueOf(type: KType): StringConverter? {
  try {
    @Suppress("UNCHECKED_CAST")
    val javaClass = type.javaType as Class<Any>?
    val valueOfMethod = javaClass?.getMethod("valueOf", String::class.java)
    if (valueOfMethod != null) {
      return { param -> valueOfMethod(null, param) }
    }
  } catch (e: ClassCastException) {
  } catch (e: NoSuchMethodException) {
  }
  return null
}
