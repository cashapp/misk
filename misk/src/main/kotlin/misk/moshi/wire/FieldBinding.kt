package misk.moshi.wire

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.wire.Message
import com.squareup.wire.WireField
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

internal class FieldBinding(
        wireField: WireField,
        builderType: Class<Message.Builder<*, *>>,
        private val messageField: Field,
        moshi: Moshi
) {
    @Suppress("UNCHECKED_CAST")
    val adapter: JsonAdapter<Any> = moshi.adapter(messageField.type) as JsonAdapter<Any>
    val name: String = messageField.name

    private val label = wireField.label
    private val isMap = wireField.keyAdapter.isNotEmpty()
    private val isList: Boolean = label == WireField.Label.PACKED || label == WireField.Label.REPEATED
    private val builderField = getBuilderField(builderType, name)
    private val builderMethod = getBuilderMethod(builderType, name, messageField.type)

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
    }
}


