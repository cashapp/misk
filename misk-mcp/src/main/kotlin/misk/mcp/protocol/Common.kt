@file:OptIn(ExperimentalSerializationApi::class)

package misk.mcp.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** The protocol revision this package implements. */
const val LATEST_PROTOCOL_VERSION: String = "2026-07-28"

/** Modern protocol versions: version, identity and capabilities are carried on every request. */
val SUPPORTED_PROTOCOL_VERSIONS: List<String> = listOf(LATEST_PROTOCOL_VERSION)

/** The newest legacy protocol version, negotiated with an `initialize` handshake. */
const val LATEST_LEGACY_PROTOCOL_VERSION: String = "2025-11-25"

/** Legacy protocol versions, which start with an `initialize` request. */
val SUPPORTED_LEGACY_PROTOCOL_VERSIONS: List<String> =
  listOf(LATEST_LEGACY_PROTOCOL_VERSION, "2025-06-18", "2025-03-26", "2024-11-05")

/** The version assumed for a Streamable HTTP request without an `MCP-Protocol-Version` header. */
const val DEFAULT_NEGOTIATED_PROTOCOL_VERSION: String = "2025-03-26"

val EmptyJsonObject: JsonObject = JsonObject(emptyMap())

internal val EmptyJsonArray: JsonArray = JsonArray(emptyList())

/** Reserved `_meta` keys. */
object McpMetaKeys {
  const val PROGRESS_TOKEN = "progressToken"
  const val PROTOCOL_VERSION = "io.modelcontextprotocol/protocolVersion"
  const val CLIENT_INFO = "io.modelcontextprotocol/clientInfo"
  const val CLIENT_CAPABILITIES = "io.modelcontextprotocol/clientCapabilities"
  const val LOG_LEVEL = "io.modelcontextprotocol/logLevel"
  const val SERVER_INFO = "io.modelcontextprotocol/serverInfo"
  const val SUBSCRIPTION_ID = "io.modelcontextprotocol/subscriptionId"
}

/** Streamable HTTP request headers. Values that aren't plain ASCII are sent as `=?base64?<base64 of UTF-8>?=`. */
object McpHeaders {
  const val PROTOCOL_VERSION = "MCP-Protocol-Version"
  const val METHOD = "Mcp-Method"

  /** The tool or prompt name, or the resource URI, of `tools/call`, `prompts/get` and `resources/read`. */
  const val NAME = "Mcp-Name"
}

/** JSON-RPC method names. */
object McpMethod {
  const val SERVER_DISCOVER = "server/discover"
  const val TOOLS_LIST = "tools/list"
  const val TOOLS_CALL = "tools/call"
  const val PROMPTS_LIST = "prompts/list"
  const val PROMPTS_GET = "prompts/get"
  const val RESOURCES_LIST = "resources/list"
  const val RESOURCES_TEMPLATES_LIST = "resources/templates/list"
  const val RESOURCES_READ = "resources/read"
  const val COMPLETION_COMPLETE = "completion/complete"
  const val ELICITATION_CREATE = "elicitation/create"
  const val NOTIFICATIONS_PROGRESS = "notifications/progress"
  const val NOTIFICATIONS_CANCELLED = "notifications/cancelled"

  /** Legacy only. */
  const val INITIALIZE = "initialize"

  /** Legacy only. */
  const val NOTIFICATIONS_INITIALIZED = "notifications/initialized"

  /** Legacy only. */
  const val PING = "ping"
}

/** The `resultType` of a modern result. Legacy results have no `resultType`. */
@Serializable
enum class ResultType {
  @SerialName("complete") Complete,
  @SerialName("input_required") InputRequired,
}

/** Whether shared intermediaries may cache a result. */
@Serializable
enum class CacheScope {
  @SerialName("public") Public,
  @SerialName("private") Private,
}

@Serializable
data class Icon
@JvmOverloads
constructor(val src: String, val mimeType: String? = null, val sizes: List<String>? = null, val theme: Theme? = null) {
  @Serializable
  enum class Theme {
    @SerialName("light") Light,
    @SerialName("dark") Dark,
  }
}

/** Name and version of an MCP client or server. */
@Serializable
data class Implementation
@JvmOverloads
constructor(
  val name: String,
  val version: String,
  val title: String? = null,
  val websiteUrl: String? = null,
  val icons: List<Icon>? = null,
  val description: String? = null,
)

@Serializable
enum class Role {
  @SerialName("user") User,
  @SerialName("assistant") Assistant,
}

@Serializable
data class Annotations
@JvmOverloads
constructor(val audience: List<Role>? = null, val priority: Double? = null, val lastModified: String? = null) {
  init {
    require(priority == null || priority in 0.0..1.0) { "Priority must be between 0.0 and 1.0" }
  }
}

/** Request parameters with no fields beyond `_meta`. */
@Serializable data class BaseRequestParams @JvmOverloads constructor(@SerialName("_meta") val meta: RequestMeta? = null)

@Serializable
data class PaginatedRequestParams
@JvmOverloads
constructor(val cursor: String? = null, @SerialName("_meta") val meta: RequestMeta? = null)

/** The result of a request that returns nothing, such as legacy `ping`. */
@Serializable data class EmptyResult @JvmOverloads constructor(@SerialName("_meta") val meta: JsonObject? = null)
