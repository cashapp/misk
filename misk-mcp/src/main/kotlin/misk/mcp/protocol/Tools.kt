@file:OptIn(ExperimentalSerializationApi::class)

package misk.mcp.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class Tool
@JvmOverloads
constructor(
  val name: String,
  val inputSchema: ToolSchema,
  val description: String? = null,
  val outputSchema: ToolSchema? = null,
  val title: String? = null,
  val annotations: ToolAnnotations? = null,
  val icons: List<Icon>? = null,
  @SerialName("_meta") val meta: JsonObject? = null,
)

/** A JSON Schema for tool input or output. The root is always an object. */
@Serializable
data class ToolSchema
@JvmOverloads
constructor(
  @SerialName("\$schema") val schema: String? = null,
  val properties: JsonObject? = null,
  val required: List<String>? = null,
  @SerialName("\$defs") val defs: JsonObject? = null,
) {
  @EncodeDefault val type: String = "object"
}

@Serializable
data class ToolAnnotations
@JvmOverloads
constructor(
  val title: String? = null,
  val readOnlyHint: Boolean? = null,
  val destructiveHint: Boolean? = null,
  val idempotentHint: Boolean? = null,
  val openWorldHint: Boolean? = null,
)

@Serializable
data class CallToolRequest(val params: CallToolRequestParams) {
  val method: String
    get() = McpMethod.TOOLS_CALL

  val name: String
    get() = params.name

  val arguments: JsonObject?
    get() = params.arguments

  val meta: RequestMeta?
    get() = params.meta

  val inputResponses: Map<String, JsonElement>?
    get() = params.inputResponses

  val requestState: String?
    get() = params.requestState
}

@Serializable
data class CallToolRequestParams
@JvmOverloads
constructor(
  val name: String,
  val arguments: JsonObject? = null,
  @SerialName("_meta") val meta: RequestMeta? = null,
  val inputResponses: Map<String, JsonElement>? = null,
  val requestState: String? = null,
)

/**
 * The result of a tool call. Tool execution failures set [isError]; protocol failures are JSON-RPC errors instead.
 *
 * [structuredContent] may be any JSON value.
 */
@Serializable
data class CallToolResult
@JvmOverloads
constructor(
  val content: List<ContentBlock>,
  val isError: Boolean? = null,
  val structuredContent: JsonElement? = null,
  @SerialName("_meta") val meta: JsonObject? = null,
) {
  companion object {
    fun success(content: String, meta: JsonObject? = null) =
      CallToolResult(content = listOf(TextContent(content)), isError = false, meta = meta)

    fun error(content: String, meta: JsonObject? = null) =
      CallToolResult(content = listOf(TextContent(content)), isError = true, meta = meta)
  }
}

@Serializable
data class ListToolsRequest @JvmOverloads constructor(val params: PaginatedRequestParams? = null) {
  val method: String
    get() = McpMethod.TOOLS_LIST
}

@Serializable
data class ListToolsResult
@JvmOverloads
constructor(val tools: List<Tool>, val nextCursor: String? = null, @SerialName("_meta") val meta: JsonObject? = null)
