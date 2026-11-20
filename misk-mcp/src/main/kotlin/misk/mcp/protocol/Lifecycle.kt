package misk.mcp.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class DiscoverRequest @JvmOverloads constructor(val params: BaseRequestParams? = null) {
  val method: String
    get() = McpMethod.SERVER_DISCOVER
}

@Serializable
data class DiscoverResult
@JvmOverloads
constructor(
  val supportedVersions: List<String>,
  val capabilities: ServerCapabilities,
  val instructions: String? = null,
  @SerialName("_meta") val meta: JsonObject? = null,
)

/** Legacy only: opens a 2025-x session. Modern clients send version and capabilities on every request instead. */
@Serializable
data class InitializeRequest(val params: InitializeRequestParams) {
  val method: String
    get() = McpMethod.INITIALIZE
}

@Serializable
data class InitializeRequestParams
@JvmOverloads
constructor(
  val protocolVersion: String,
  val capabilities: ClientCapabilities,
  val clientInfo: Implementation,
  @SerialName("_meta") val meta: RequestMeta? = null,
)

@Serializable
data class InitializeResult
@JvmOverloads
constructor(
  val protocolVersion: String,
  val capabilities: ServerCapabilities,
  val serverInfo: Implementation,
  val instructions: String? = null,
  @SerialName("_meta") val meta: JsonObject? = null,
)

@Serializable
data class ProgressNotification(val params: ProgressNotificationParams) {
  val method: String
    get() = McpMethod.NOTIFICATIONS_PROGRESS
}

@Serializable
data class ProgressNotificationParams
@JvmOverloads
constructor(
  val progressToken: ProgressToken,
  val progress: Double,
  val total: Double? = null,
  val message: String? = null,
  @SerialName("_meta") val meta: JsonObject? = null,
)
