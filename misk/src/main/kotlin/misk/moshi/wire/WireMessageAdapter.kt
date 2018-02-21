package misk.moshi.wire

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.wire.Message
import com.squareup.wire.WireField
import java.lang.reflect.Type

/** Json marshaling for Wire messages, correctly using Builders to construct properly formed type */
internal class WireMessageAdapter(
        messageType: Class<Message<*, *>>,
        private val moshi: Moshi
) : JsonAdapter<Any?>() {
    @Suppress("UNCHECKED_CAST")
    private val builderType = try {
        Class.forName("${messageType.name}\$Builder")
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
            fieldBindings[fieldName]?.let { binding ->
                binding.adapter.fromJson(reader)?.let { binding.set(builder, it) }
            } ?: reader.skipValue()
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
                if (shouldEmitField(fieldValue)) {
                    writer.name(fieldName)
                    binding.adapter.toJson(writer, fieldValue)
                }
            }
            writer.endObject()
        }
    }

    private fun shouldEmitField(value: Any?) = when (value) {
        null -> false
        is List<*> -> value.isNotEmpty()
        is Map<*, *> -> value.isNotEmpty()
        else -> true
    }

    class Factory : JsonAdapter.Factory {
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