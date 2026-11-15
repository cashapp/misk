package misk.mcp

import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.types.BooleanSchema
import io.modelcontextprotocol.kotlin.sdk.types.DoubleSchema
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import io.modelcontextprotocol.kotlin.sdk.types.IntegerSchema
import io.modelcontextprotocol.kotlin.sdk.types.PrimitiveSchemaDefinition
import io.modelcontextprotocol.kotlin.sdk.types.StringSchema
import io.modelcontextprotocol.kotlin.sdk.types.UntitledSingleSelectEnumSchema
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.json.JsonObject
import misk.annotation.ExperimentalMiskApi
import misk.mcp.action.currentServerSession

/**
 * Type-safe result wrapper for MCP elicitation responses.
 *
 * Provides a strongly-typed alternative to the raw [ElicitResult] from the MCP Kotlin SDK. Automatically deserializes
 * the response content to the specified type [T].
 *
 * @param T The expected type of the elicitation response content, must be serializable
 * @property action The client's response action (accept, decline, or cancel)
 * @property content The deserialized response content, null if action is not accept or if parsing failed
 * @property _meta Additional metadata from the elicitation response
 */
data class TypedCreateElicitationResult<T : Any>(
  val action: ElicitResult.Action,
  val content: T?,
  @Suppress("PropertyName") val _meta: JsonObject?,
)

/**
 * Creates a type-safe elicitation request to gather structured input from the client.
 *
 * Provides a strongly-typed wrapper around the MCP Kotlin SDK's [ServerSession.createElicitation] method. Automatically
 * generates the JSON schema from the specified Kotlin type [T] and deserializes the client's response to that type.
 *
 * @param T The expected type of the elicitation response content, must be a serializable data class
 * @param message Human-readable message explaining what information is being requested
 * @param options Optional request configuration including timeout and progress callbacks
 * @return [TypedCreateElicitationResult] containing the client's response with type-safe content
 */
@OptIn(ExperimentalMiskApi::class)
suspend inline fun <reified T : Any> createTypedElicitation(
  message: String,
  options: RequestOptions? = null,
): TypedCreateElicitationResult<T> {
  val (properties, required) = typeOf<T>().toElicitationSchema()
  return currentServerSession()
    .createElicitation(
      message = message,
      requestedSchema = ElicitRequestParams.RequestedSchema(properties = properties, required = required),
      options = options,
    )
    .let { result ->
      TypedCreateElicitationResult(action = result.action, content = result.content?.decode<T>(), _meta = result.meta)
    }
}

/**
 * Builds an elicitation [RequestedSchema] directly from the [SerialDescriptor] of a Kotlin type.
 *
 * Maps each property's [SerialKind] to a [PrimitiveSchemaDefinition]:
 * - [PrimitiveKind.STRING], [PrimitiveKind.CHAR] → [StringSchema]
 * - [PrimitiveKind.INT], [PrimitiveKind.LONG], [PrimitiveKind.SHORT], [PrimitiveKind.BYTE] → [IntegerSchema]
 * - [PrimitiveKind.FLOAT], [PrimitiveKind.DOUBLE] → [DoubleSchema]
 * - [PrimitiveKind.BOOLEAN] → [BooleanSchema]
 * - [SerialKind.ENUM] → [UntitledSingleSelectEnumSchema] with enum constant names
 * - All other kinds fall back to [StringSchema]
 *
 * @return a pair of (properties map, required property names)
 */
@PublishedApi
@OptIn(ExperimentalSerializationApi::class)
internal fun KType.toElicitationSchema(): Pair<Map<String, PrimitiveSchemaDefinition>, List<String>> {
  val descriptor = serializer().descriptor
  val properties = mutableMapOf<String, PrimitiveSchemaDefinition>()
  val required = mutableListOf<String>()

  for (index in 0 until descriptor.elementsCount) {
    val name = descriptor.getElementName(index)
    val elementDescriptor = descriptor.getElementDescriptor(index)
    val description = descriptor.getElementAnnotations(index).filterIsInstance<Description>().firstOrNull()?.value

    properties[name] =
      when (elementDescriptor.kind) {
        SerialKind.ENUM ->
          UntitledSingleSelectEnumSchema(
            description = description,
            enumValues = (0 until elementDescriptor.elementsCount).map { elementDescriptor.getElementName(it) },
          )
        PrimitiveKind.INT,
        PrimitiveKind.LONG,
        PrimitiveKind.SHORT,
        PrimitiveKind.BYTE -> IntegerSchema(description = description)
        PrimitiveKind.FLOAT,
        PrimitiveKind.DOUBLE -> DoubleSchema(description = description)
        PrimitiveKind.BOOLEAN -> BooleanSchema(description = description)
        else -> StringSchema(description = description)
      }

    if (!descriptor.isElementOptional(index) && !elementDescriptor.isNullable) {
      required.add(name)
    }
  }

  return properties to required
}
