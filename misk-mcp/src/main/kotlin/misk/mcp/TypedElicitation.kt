package misk.mcp

import io.modelcontextprotocol.kotlin.sdk.CreateElicitationRequest.RequestedSchema
import io.modelcontextprotocol.kotlin.sdk.CreateElicitationResult
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import misk.annotation.ExperimentalMiskApi
import misk.mcp.action.currentServerSession
import misk.mcp.internal.McpJson
import misk.mcp.internal.generateJsonSchema

/**
 * Type-safe result wrapper for MCP elicitation responses.
 *
 * Provides a strongly-typed alternative to the raw [CreateElicitationResult] from the MCP Kotlin SDK.
 * Automatically deserializes the response content to the specified type [T].
 *
 * @param T The expected type of the elicitation response content, must be serializable
 * @property action The client's response action (accept, decline, or cancel)
 * @property content The deserialized response content, null if action is not accept or if parsing failed
 * @property _meta Additional metadata from the elicitation response
 */
data class TypedCreateElicitationResult<T : Any>(
  val action: CreateElicitationResult.Action,
  val content: T?,
  @Suppress("PropertyName") val _meta: JsonObject,
)

/**
 * Creates a type-safe elicitation request to gather structured input from the client.
 *
 * Provides a strongly-typed wrapper around the MCP Kotlin SDK's [ServerSession.createElicitation] method.
 * Automatically generates the JSON schema from the specified Kotlin type [T] and deserializes the client's
 * response to that type.
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
): TypedCreateElicitationResult<T> = currentServerSession()
  .createElicitation(
    message = message,
    requestedSchema = T::class.generateJsonSchema().let { schema ->
      RequestedSchema(
        properties = requireNotNull(schema["properties"] as? JsonObject) {
          "RequestedSchema must have properties defined"
        },
        required = requireNotNull(schema["required"] as? JsonArray) {
          "RequestedSchema must have required properties defined"
        }.map { it.jsonPrimitive.content },
      )
    },
    options = options
  )
  .let { result ->
    TypedCreateElicitationResult(
      action = result.action,
      content = (result.content as? JsonElement)?.let { McpJson.decodeFromJsonElement<T>(it) },
      _meta = result._meta,
    )
  }