package misk.moshi.wire

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.WireField
import misk.moshi.adapter
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

internal class FieldBinding(
  wireField: WireField,
  builderType: Class<Message.Builder<*, *>>,
  private val messageField: Field,
  moshi: Moshi
) {
  val name: String = messageField.name

  private val label = wireField.label
  private val isMap = wireField.keyAdapter.isNotEmpty()
  private val isList: Boolean = label == WireField.Label.PACKED || label == WireField.Label.REPEATED
  private val builderField = getBuilderField(builderType, name)
  private val builderMethod = getBuilderMethod(builderType, name, messageField.type)

  @Suppress("UNCHECKED_CAST")
  val adapter: JsonAdapter<Any?> = when {
    isList -> {
      val elementAdapter = jsonAdapter(moshi, wireField.adapter) as JsonAdapter<Any?>
      ListAdapter(elementAdapter) as JsonAdapter<Any?>
    }
    isMap -> {
      val keyConverter = fromString(wireField.keyAdapter)
      val valueAdapter = jsonAdapter(moshi, wireField.adapter) as JsonAdapter<Any?>
      MapAdapter(keyConverter, valueAdapter) as JsonAdapter<Any?>
    }
    else -> moshi.adapter(messageField.type) as JsonAdapter<Any?>
  }

  @Suppress("UNCHECKED_CAST")
  fun value(builder: Message.Builder<*, *>, value: Any) {
    when {
      isList -> {
        val list = getFromBuilder(builder) as MutableList<Any>
        list.add(value)
      }
      isMap -> {
        val map = getFromBuilder(builder) as MutableMap<Any, Any>
        map.putAll(value as Map<Any, Any>)
      }
      else -> set(builder, value)
    }
  }

  fun set(builder: Message.Builder<*, *>, value: Any) {
    try {
      if (label == WireField.Label.ONE_OF) {
        // In order to maintain the 'oneof' invariant, call the builder setter method rather
        // than setting the builder field directly.
        builderMethod.invoke(builder, value)
      } else {
        builderField.set(builder, value)
      }
    } catch (e: IllegalAccessException) {
      throw AssertionError(e)
    } catch (e: InvocationTargetException) {
      throw AssertionError(e)
    }
  }

  fun get(message: Any): Any? = try {
    messageField.get(message)
  } catch (e: IllegalAccessException) {
    throw AssertionError(e)
  }

  private fun getFromBuilder(builder: Message.Builder<*, *>): Any {
    try {
      return builderField.get(builder)
    } catch (e: IllegalAccessException) {
      throw AssertionError(e)
    }
  }

  companion object {
    private fun getBuilderField(
      builderType: Class<Message.Builder<*, *>>,
      name: String
    ): Field {
      try {
        return builderType.getField(name)
      } catch (e: NoSuchFieldException) {
        throw AssertionError("No builder field ${builderType.simpleName}#$name")
      }
    }

    private fun getBuilderMethod(
      builderType: Class<Message.Builder<*, *>>,
      name: String,
      type: Class<*>
    ): Method {
      try {
        return builderType.getMethod(name, type)
      } catch (e: NoSuchMethodException) {
        throw AssertionError(
            "No builder method ${builderType.simpleName}#$name (${type.name}"
        )
      }
    }

    private fun fromString(protoAdapterName: String): (String) -> Any {
      val typeName = protoAdapterName.substringBefore("#")
      check(typeName == ProtoAdapter::class.java.name) {
        "can only convert from strings for primitive types, not $typeName"
      }

      val adapterFieldName = protoAdapterName.substringAfter("#")
      return when (adapterFieldName) {
        "INT32", "UINT32", "SINT32", "FIXED32", "SFIXED32" -> toNumber { it.toInt() }
        "INT64", "UINT64", "SINT64", "FIXED64", "SFIXED64" -> toNumber { it.toLong() }
        "BOOL" -> toNumber { it.toBoolean() }
        "FLOAT" -> toNumber { it.toFloat() }
        "DOUBLE" -> toNumber { it.toDouble() }
        "STRING" -> { s -> s }
        "BYTES" -> { s ->
          s.decodeBase64()
              ?: throw IllegalArgumentException("could not parse $s as base 64")
        }
        else -> throw IllegalStateException("unknown type $adapterFieldName")
      }
    }

    private fun toNumber(converter: (String) -> Any): (String) -> Any = {
      try {
        converter(it)
      } catch (e: NumberFormatException) {
        throw IllegalArgumentException("Invalid parameter format: $it", e)
      }
    }

    private fun jsonAdapter(moshi: Moshi, protoAdapterName: String): JsonAdapter<*> {
      val typeName = protoAdapterName.substringBefore("#")
      val adapterFieldName = protoAdapterName.substringAfter("#")
      return if (typeName == ProtoAdapter::class.java.name) when (adapterFieldName) {
        "INT32", "UINT32", "SINT32", "FIXED32", "SFIXED32" -> moshi.adapter<Int>()
        "INT64", "UINT64", "SINT64", "FIXED64", "SFIXED64" -> moshi.adapter<Long>()
        "BOOL" -> moshi.adapter<Boolean>()
        "FLOAT" -> moshi.adapter<Float>()
        "DOUBLE" -> moshi.adapter<Double>()
        "STRING" -> moshi.adapter<String>()
        "BYTES" -> moshi.adapter<ByteString>()
        else -> throw IllegalStateException("unknown type $adapterFieldName")
      } else moshi.adapter(Class.forName(typeName))
    }
  }
}


