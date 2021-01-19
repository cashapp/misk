package misk.moshi.wire

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.wire.Message
import com.squareup.wire.WireField
import java.lang.reflect.Type
import javax.inject.Inject

/** Json marshaling for Wire messages, correctly using Builders to construct properly formed type */
@Deprecated("Use WireJsonAdapterFactory instead")
internal class WireMessageAdapter(
  messageType: Class<Message<*, *>>,
  moshi: Moshi
) : JsonAdapter<Any?>() {
  @Suppress("UNCHECKED_CAST")
  private val builderType = try {
    Class.forName("${messageType.name}\$Builder", true, messageType.classLoader)
  } catch (e: ClassNotFoundException) {
    throw AssertionError("no builder for ${messageType.name}")
  } as Class<Message.Builder<*, *>>

  @Suppress("UNCHECKED_CAST")
  private val newBuilder = builderType.constructors.find {
    it.parameters.isEmpty()
  } ?: throw AssertionError("no suitable constructor for ${builderType.name}")

  private val fieldBindings: Map<String, FieldBinding> = messageType.declaredFields
      .mapNotNull { field ->
        field.getAnnotation(WireField::class.java)
            ?.let { FieldBinding(it, builderType, field, moshi) }
      }.map { it.name to it }
      .toMap()

  override fun fromJson(reader: JsonReader): Any? {
    val builder = newBuilder.newInstance() as Message.Builder<*, *>
    reader.beginObject()
    while (reader.hasNext()) {
      val fieldName = reader.nextName()
      val binding = fieldBindings[fieldName]
      if (binding != null) {
        binding.adapter.fromJson(reader)?.let { binding.set(builder, it) }
      } else {
        reader.skipValue()
      }
    }

    reader.endObject()
    return builder.build()
  }

  override fun toJson(writer: JsonWriter, value: Any?) {
    if (value == null) {
      writer.nullValue()
    } else {
      writer.beginObject()
      fieldBindings.forEach { (fieldName, binding) ->
        val fieldValue = binding.get(value)
        writer.name(fieldName)
        binding.adapter.toJson(writer, fieldValue)
      }
      writer.endObject()
    }
  }

  class Factory @Inject constructor() : JsonAdapter.Factory {
    override fun create(
      type: Type,
      annotations: Set<Annotation>,
      moshi: Moshi
    ): JsonAdapter<*>? {
      return if (type is Class<*> && type.superclass == Message::class.java) {
        @Suppress("UNCHECKED_CAST")
        val messageType = type as Class<Message<*, *>>
        WireMessageAdapter(messageType, moshi)
      } else null
    }
  }
}
