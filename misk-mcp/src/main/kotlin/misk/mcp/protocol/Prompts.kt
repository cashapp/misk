package misk.mcp.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class PromptArgument
@JvmOverloads
constructor(val name: String, val description: String? = null, val required: Boolean? = null, val title: String? = null)

@Serializable
data class Prompt
@JvmOverloads
constructor(
  val name: String,
  val description: String? = null,
  val arguments: List<PromptArgument>? = null,
  val title: String? = null,
  val icons: List<Icon>? = null,
  @SerialName("_meta") val meta: JsonObject? = null,
)

@Serializable data class PromptMessage(val role: Role, val content: ContentBlock)

@Serializable
data class GetPromptRequest(val params: GetPromptRequestParams) {
  val method: String
    get() = McpMethod.PROMPTS_GET

  val name: String
    get() = params.name

  val arguments: Map<String, String>?
    get() = params.arguments

  val meta: RequestMeta?
    get() = params.meta

  val inputResponses: Map<String, JsonElement>?
    get() = params.inputResponses

  val requestState: String?
    get() = params.requestState
}

@Serializable
data class GetPromptRequestParams
@JvmOverloads
constructor(
  val name: String,
  val arguments: Map<String, String>? = null,
  @SerialName("_meta") val meta: RequestMeta? = null,
  val inputResponses: Map<String, JsonElement>? = null,
  val requestState: String? = null,
)

@Serializable
data class GetPromptResult
@JvmOverloads
constructor(
  val messages: List<PromptMessage>,
  val description: String? = null,
  @SerialName("_meta") val meta: JsonObject? = null,
)

@Serializable
data class ListPromptsRequest @JvmOverloads constructor(val params: PaginatedRequestParams? = null) {
  val method: String
    get() = McpMethod.PROMPTS_LIST
}

@Serializable
data class ListPromptsResult
@JvmOverloads
constructor(
  val prompts: List<Prompt>,
  val nextCursor: String? = null,
  @SerialName("_meta") val meta: JsonObject? = null,
)
