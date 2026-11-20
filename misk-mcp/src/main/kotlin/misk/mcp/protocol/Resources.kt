package misk.mcp.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class Resource
@JvmOverloads
constructor(
  val uri: String,
  val name: String,
  val description: String? = null,
  val mimeType: String? = null,
  val size: Long? = null,
  val title: String? = null,
  val annotations: Annotations? = null,
  val icons: List<Icon>? = null,
  @SerialName("_meta") val meta: JsonObject? = null,
)

@Serializable
data class ResourceTemplate
@JvmOverloads
constructor(
  val uriTemplate: String,
  val name: String,
  val description: String? = null,
  val mimeType: String? = null,
  val title: String? = null,
  val annotations: Annotations? = null,
  val icons: List<Icon>? = null,
  @SerialName("_meta") val meta: JsonObject? = null,
)

@Serializable
data class ReadResourceRequest(val params: ReadResourceRequestParams) {
  val method: String
    get() = McpMethod.RESOURCES_READ

  val uri: String
    get() = params.uri

  val meta: RequestMeta?
    get() = params.meta

  val inputResponses: Map<String, JsonElement>?
    get() = params.inputResponses

  val requestState: String?
    get() = params.requestState
}

@Serializable
data class ReadResourceRequestParams
@JvmOverloads
constructor(
  val uri: String,
  @SerialName("_meta") val meta: RequestMeta? = null,
  val inputResponses: Map<String, JsonElement>? = null,
  val requestState: String? = null,
)

@Serializable
data class ReadResourceResult
@JvmOverloads
constructor(val contents: List<ResourceContents>, @SerialName("_meta") val meta: JsonObject? = null)

@Serializable
data class ListResourcesRequest @JvmOverloads constructor(val params: PaginatedRequestParams? = null) {
  val method: String
    get() = McpMethod.RESOURCES_LIST
}

@Serializable
data class ListResourcesResult
@JvmOverloads
constructor(
  val resources: List<Resource>,
  val nextCursor: String? = null,
  @SerialName("_meta") val meta: JsonObject? = null,
)

@Serializable
data class ListResourceTemplatesRequest @JvmOverloads constructor(val params: PaginatedRequestParams? = null) {
  val method: String
    get() = McpMethod.RESOURCES_TEMPLATES_LIST
}

@Serializable
data class ListResourceTemplatesResult
@JvmOverloads
constructor(
  val resourceTemplates: List<ResourceTemplate>,
  val nextCursor: String? = null,
  @SerialName("_meta") val meta: JsonObject? = null,
)
