@file:Suppress("unused")

package misk.mcp

import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.serializer
import kotlin.reflect.KType

/**
 * JSON serialization instance configured for the misk-mcp module.
 * Used to deserialize incoming MCP requests and serialize outgoing MCP responses.
 */
@OptIn(ExperimentalSerializationApi::class)
@PublishedApi
internal val McpJson: Json by lazy {
  Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    classDiscriminatorMode = ClassDiscriminatorMode.POLYMORPHIC
    explicitNulls = false
  }
}

/**
 * Retrieves a [KSerializer] for this [KType] from the [McpJson] serializers module.
 *
 * @receiver the [KType] to get a serializer for
 * @return the [KSerializer] for this type
 * @throws IllegalArgumentException if no serializer is found for the type
 */
@Suppress("UNCHECKED_CAST")
internal fun KType.serializer(): KSerializer<Any?> =
  try {
    McpJson.serializersModule.serializer(this)
  } catch (e: SerializationException) {
    throw IllegalArgumentException(
      "No serializer found for class $this",
      e,
    )
  }

/**
 * Casts this [KSerializer] to a [KSerializer] of type [T].
 *
 * @receiver the [KSerializer] to cast
 * @return the [KSerializer] cast to type [T]
 */
@Suppress("NOTHING_TO_INLINE", "UNCHECKED_CAST")
internal inline fun <T> KSerializer<*>.cast(): KSerializer<T> = this as KSerializer<T>

/**
 * A reusable empty [JsonArray] instance.
 */
val EmptyJsonArray: JsonArray = JsonArray(emptyList())

/**
 * Returns a default [JsonElement] based on the reified type [T].
 *
 * @return [EmptyJsonObject] for [JsonObject], [EmptyJsonArray] for [JsonArray], or [JsonNull] otherwise
 */
inline fun <reified T : JsonElement> defaultJsonElement(): T = when (T::class) {
  JsonObject::class -> EmptyJsonObject as T
  JsonArray::class -> EmptyJsonArray as T
  else -> JsonNull as T
}

/**
 * Encodes this object to a [JsonElement] using the [McpJson] serializer.
 *
 * @receiver the object to encode, or null
 * @return the encoded [JsonElement], or a default empty element if the receiver is null
 * @throws ClassCastException if the encoded result cannot be cast to [R]
 */
inline fun <reified T : Any, reified R : JsonElement> T?.encode(): R =
  this?.let { McpJson.encodeToJsonElement(it) as R } ?: defaultJsonElement()

/**
 * Encodes this object to a JSON string using the [McpJson] serializer.
 *
 * @receiver the object to encode, or null
 * @return the JSON string representation, or an empty string if the receiver is null
 */
inline fun <reified T : Any> T?.encodeToString(): String =
  this?.let { McpJson.encodeToString(it) } ?: ""

/**
 * Encodes this object to a [JsonElement] using the [McpJson] serializer and the provided [serializer].
 *
 * @receiver the object to encode, or null
 * @param serializer the serialization strategy to use
 * @return the encoded [JsonElement], or a default empty element if the receiver is null
 * @throws ClassCastException if the encoded result cannot be cast to [R]
 */
@Suppress("NOTHING_TO_INLINE")
inline fun <T : Any, reified R : JsonElement> T?.encode(serializer: SerializationStrategy<T>): R =
  this?.let { McpJson.encodeToJsonElement(serializer, it) as R } ?: defaultJsonElement()

/**
 * Encodes this object to a JSON string using the [McpJson] serializer and the provided [serializer].
 *
 * @receiver the object to encode, or null
 * @param serializer the serialization strategy to use
 * @return the JSON string representation, or an empty string if the receiver is null
 */
@Suppress("NOTHING_TO_INLINE")
inline fun <T : Any> T?.encodeToString(serializer: SerializationStrategy<T>): String =
  this?.let { McpJson.encodeToString(serializer, it) } ?: ""

/**
 * Decodes this [JsonObject] to an object of type [T] using the [McpJson] serializer.
 *
 * @receiver the [JsonObject] to decode
 * @return the deserialized object of type [T]
 */
inline fun <reified T : Any> JsonObject.decode(): T =
  McpJson.decodeFromJsonElement<T>(this)

/**
 * Decodes this [JsonObject] to an object of type [T] using the [McpJson] serializer and the provided [deserializer].
 *
 * @receiver the [JsonObject] to decode
 * @param deserializer the deserialization strategy to use
 * @return the deserialized object of type [T]
 */
@Suppress("NOTHING_TO_INLINE")
inline fun <T : Any> JsonObject.decode(deserializer: DeserializationStrategy<T>): T =
  McpJson.decodeFromJsonElement(deserializer, this)

/**
 * Decodes this JSON string to an object of type [T] using the [McpJson] serializer.
 *
 * @receiver the JSON string to decode
 * @return the deserialized object of type [T]
 */
inline fun <reified T : Any> String.decode(): T =
  McpJson.decodeFromString<T>(this)

/**
 * Decodes this JSON string to an object of type [T] using the [McpJson] serializer and the provided [deserializer].
 *
 * @receiver the JSON string to decode
 * @param deserializer the deserialization strategy to use
 * @return the deserialized object of type [T]
 */
@Suppress("NOTHING_TO_INLINE")
inline fun <T : Any> String.decode(deserializer: DeserializationStrategy<T>): T =
  McpJson.decodeFromString(deserializer, this)

