@file:OptIn(ExperimentalSerializationApi::class)

package misk.mcp.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Asks the client to collect information from the user. Sent as an input request of an [InputRequiredResult]. */
@Serializable
data class ElicitRequest(val params: ElicitRequestParams) : InputRequest {
  @EncodeDefault override val method: String = McpMethod.ELICITATION_CREATE
}

@Serializable(with = ElicitRequestParamsSerializer::class)
sealed interface ElicitRequestParams {
  val mode: String
  val message: String
}

/** Collects structured data from the user in a form the client renders from [requestedSchema]. */
@Serializable
data class ElicitRequestFormParams(override val message: String, val requestedSchema: RequestedSchema) :
  ElicitRequestParams {
  @EncodeDefault override val mode: String = MODE

  companion object {
    const val MODE = "form"
  }
}

/** Sends the user to [url] for an interaction that must not pass through the client, such as entering a secret. */
@Serializable
data class ElicitRequestURLParams(override val message: String, val url: String) : ElicitRequestParams {
  @EncodeDefault override val mode: String = MODE

  companion object {
    const val MODE = "url"
  }
}

/** A flat object schema whose properties are primitive values. */
@Serializable
data class RequestedSchema
@JvmOverloads
constructor(val properties: Map<String, PrimitiveSchemaDefinition>, val required: List<String>? = null) {
  @EncodeDefault val type: String = "object"
}

@Serializable(with = PrimitiveSchemaDefinitionSerializer::class)
sealed interface PrimitiveSchemaDefinition {
  val type: String
  val title: String?
  val description: String?
}

@Serializable
data class StringSchema
@JvmOverloads
constructor(
  override val title: String? = null,
  override val description: String? = null,
  val minLength: Int? = null,
  val maxLength: Int? = null,
  val format: Format? = null,
  val default: String? = null,
) : PrimitiveSchemaDefinition {
  @EncodeDefault override val type: String = "string"

  @Serializable
  enum class Format {
    @SerialName("email") Email,
    @SerialName("uri") Uri,
    @SerialName("date") Date,
    @SerialName("date-time") DateTime,
  }
}

@Serializable
data class IntegerSchema
@JvmOverloads
constructor(
  override val title: String? = null,
  override val description: String? = null,
  val minimum: Long? = null,
  val maximum: Long? = null,
  val default: Long? = null,
) : PrimitiveSchemaDefinition {
  @EncodeDefault override val type: String = "integer"
}

@Serializable
data class NumberSchema
@JvmOverloads
constructor(
  override val title: String? = null,
  override val description: String? = null,
  val minimum: Double? = null,
  val maximum: Double? = null,
  val default: Double? = null,
) : PrimitiveSchemaDefinition {
  @EncodeDefault override val type: String = "number"
}

@Serializable
data class BooleanSchema
@JvmOverloads
constructor(
  override val title: String? = null,
  override val description: String? = null,
  val default: Boolean? = null,
) : PrimitiveSchemaDefinition {
  @EncodeDefault override val type: String = "boolean"
}

@Serializable
data class UntitledSingleSelectEnumSchema
@JvmOverloads
constructor(
  @SerialName("enum") val enumValues: List<String>,
  override val title: String? = null,
  override val description: String? = null,
  val default: String? = null,
) : PrimitiveSchemaDefinition {
  @EncodeDefault override val type: String = "string"
}

@Serializable
data class TitledSingleSelectEnumSchema
@JvmOverloads
constructor(
  val oneOf: List<EnumOption>,
  override val title: String? = null,
  override val description: String? = null,
  val default: String? = null,
) : PrimitiveSchemaDefinition {
  @EncodeDefault override val type: String = "string"
}

@Serializable
data class UntitledMultiSelectEnumSchema
@JvmOverloads
constructor(
  val items: Items,
  override val title: String? = null,
  override val description: String? = null,
  val minItems: Int? = null,
  val maxItems: Int? = null,
  val default: List<String>? = null,
) : PrimitiveSchemaDefinition {
  @EncodeDefault override val type: String = "array"

  @Serializable
  data class Items(@SerialName("enum") val enumValues: List<String>) {
    @EncodeDefault val type: String = "string"
  }
}

@Serializable
data class TitledMultiSelectEnumSchema
@JvmOverloads
constructor(
  val items: Items,
  override val title: String? = null,
  override val description: String? = null,
  val minItems: Int? = null,
  val maxItems: Int? = null,
  val default: List<String>? = null,
) : PrimitiveSchemaDefinition {
  @EncodeDefault override val type: String = "array"

  @Serializable data class Items(val anyOf: List<EnumOption>)
}

@Serializable data class EnumOption(@SerialName("const") val value: String, val title: String)

/** The user's response to an [ElicitRequest]. [content] is present only when the user accepted a form. */
@Serializable
data class ElicitResult
@JvmOverloads
constructor(val action: Action, val content: JsonObject? = null, @SerialName("_meta") val meta: JsonObject? = null) {
  @Serializable
  enum class Action {
    @SerialName("accept") Accept,
    @SerialName("decline") Decline,
    @SerialName("cancel") Cancel,
  }
}

internal object ElicitRequestParamsSerializer :
  JsonContentPolymorphicSerializer<ElicitRequestParams>(ElicitRequestParams::class) {
  override fun selectDeserializer(element: JsonElement) =
    when (val mode = element.jsonObject["mode"]?.jsonPrimitive?.content) {
      null,
      ElicitRequestFormParams.MODE -> ElicitRequestFormParams.serializer()
      ElicitRequestURLParams.MODE -> ElicitRequestURLParams.serializer()
      else -> throw SerializationException("Unknown elicitation mode: $mode")
    }
}

internal object PrimitiveSchemaDefinitionSerializer :
  JsonContentPolymorphicSerializer<PrimitiveSchemaDefinition>(PrimitiveSchemaDefinition::class) {
  override fun selectDeserializer(element: JsonElement) =
    element.jsonObject.let { schema ->
      when (val type = schema["type"]?.jsonPrimitive?.content) {
        "integer" -> IntegerSchema.serializer()
        "number" -> NumberSchema.serializer()
        "boolean" -> BooleanSchema.serializer()
        "array" ->
          if ("anyOf" in schema["items"]?.jsonObject.orEmpty()) TitledMultiSelectEnumSchema.serializer()
          else UntitledMultiSelectEnumSchema.serializer()
        "string" ->
          when {
            "oneOf" in schema -> TitledSingleSelectEnumSchema.serializer()
            "enum" in schema -> UntitledSingleSelectEnumSchema.serializer()
            else -> StringSchema.serializer()
          }
        else -> throw SerializationException("Unknown primitive schema type: $type")
      }
    }
}
