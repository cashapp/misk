package misk.mcp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.longOrNull

/**
 * The `_meta` object of a request. Typed accessors read the reserved keys; [get] reads any other key.
 *
 * Accessors return null when a key is absent or malformed. The server validates the required keys of modern requests
 * before a handler runs.
 */
@JvmInline
@Serializable
value class RequestMeta(val json: JsonObject) {
  val progressToken: ProgressToken?
    get() =
      (json[McpMetaKeys.PROGRESS_TOKEN] as? JsonPrimitive)?.let { element ->
        when {
          element.isString -> ProgressToken(element.content)
          else -> element.longOrNull?.let { ProgressToken(it) }
        }
      }

  val protocolVersion: String?
    get() = (json[McpMetaKeys.PROTOCOL_VERSION] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

  val clientInfo: Implementation?
    get() = json[McpMetaKeys.CLIENT_INFO]?.decodeOrNull()

  val clientCapabilities: ClientCapabilities?
    get() = json[McpMetaKeys.CLIENT_CAPABILITIES]?.decodeOrNull()

  operator fun get(key: String): JsonElement? = json[key]

  private inline fun <reified T> JsonElement.decodeOrNull(): T? =
    try {
      lenientJson.decodeFromJsonElement<T>(this)
    } catch (_: SerializationException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    }

  private companion object {
    val lenientJson = Json { ignoreUnknownKeys = true }
  }
}
