package misk.web.extractors

import com.squareup.moshi.rawType
import kotlin.jvm.Throws
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubtypeOf
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

/**
 * Converts from request input to a parsed value.
 *
 * The result can be any number of things, such as a path param, query param, request cookie, or request header.
 */
fun interface StringConverter {
  /**
   * If the given String can't be converted, throws an IllegalArgumentException.
   */
  @Throws(IllegalArgumentException::class)
  fun convert(string: String): Any?

  /**
   * Returns a StringConverter that can deserialize a String to the correct type for the KType, or returns null
   * if it can't handle that type.
   */
  interface Factory {
    fun create(kType: KType): StringConverter?
  }
}

fun converterFor(
  type: KType,
  factories: List<StringConverter.Factory> = listOf(),
): StringConverter? {
  return factories.firstNotNullOfOrNull { it.create(type) }
    ?: when {
      type.isSubtypeOf(stringType) -> StringConverter { it }
      type == stringTypeNullable -> StringConverter { it }
      type.isSubtypeOf(intType) -> numberConversionWrapper { it.toInt() }
      type == intTypeNullable -> numberConversionWrapper { it.toInt() }
      type.isSubtypeOf(longType) -> numberConversionWrapper { it.toLong() }
      type == longTypeNullable -> numberConversionWrapper { it.toLong() }
      type.isSubtypeOf(doubleType) -> numberConversionWrapper { it.toDouble() }
      type == doubleTypeNullable -> numberConversionWrapper { it.toDouble() }
      type.isSubtypeOf(booleanType) -> numberConversionWrapper { it.toBoolean() }
      type == booleanTypeNullable -> numberConversionWrapper { it.toBoolean() }
      else -> createFromValueOf(type)
    }
}

private fun numberConversionWrapper(wrappedConverter: StringConverter): StringConverter {
  return StringConverter {
    try {
      wrappedConverter.convert(it)
    } catch (e: NumberFormatException) {
      throw IllegalArgumentException("Invalid parameter format: $it", e)
    }
  }
}

private fun createFromValueOf(type: KType): StringConverter? {
  try {
    val javaClass = type.javaType.rawType
    val valueOfMethod = javaClass.getMethod("valueOf", String::class.java)
    return StringConverter { param -> valueOfMethod(null, param) }
  } catch (_: ClassCastException) {
  } catch (_: NoSuchMethodException) {
  }
  return null
}
