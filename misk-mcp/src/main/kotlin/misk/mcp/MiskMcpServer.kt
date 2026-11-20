@file:OptIn(ExperimentalMiskApi::class)

package misk.mcp

import com.google.inject.Provider
import misk.annotation.ExperimentalMiskApi
import misk.mcp.config.McpIconConfig
import misk.mcp.config.McpServerConfig
import misk.mcp.internal.McpDispatcher
import misk.mcp.internal.RequestStateCodec
import misk.mcp.internal.UriTemplate
import misk.mcp.protocol.EmptyJsonObject
import misk.mcp.protocol.Icon
import misk.mcp.protocol.Implementation
import misk.mcp.protocol.Prompt
import misk.mcp.protocol.PromptReference
import misk.mcp.protocol.Reference
import misk.mcp.protocol.Resource
import misk.mcp.protocol.ResourceTemplate
import misk.mcp.protocol.ResourceTemplateReference
import misk.mcp.protocol.ServerCapabilities
import misk.mcp.protocol.Tool
import misk.mcp.protocol.ToolAnnotations

/**
 * Misk's Model Context Protocol (MCP) server.
 *
 * The server serves the registered [McpTool], [McpResource], [McpResourceTemplate] and [McpPrompt] implementations to
 * MCP clients over Streamable HTTP. It speaks both protocol eras:
 * - **Modern** (2026-07-28): stateless requests that carry the protocol version and client capabilities in `_meta`.
 *   Clients learn about the server with `server/discover`.
 * - **Legacy** (2025-11-25 and earlier): clients open with `initialize`. The server keeps no state between requests.
 *
 * Each handler runs inline on the web action's coroutine, so `ActionScoped` values and coroutine context elements that
 * the web action installs are visible to it.
 *
 * [McpServerModule] creates the server, and web actions use it through [misk.mcp.action.McpStreamManager]:
 * ```kotlin
 * class MyMcpWebAction @Inject constructor(
 *   private val mcpStreamManager: McpStreamManager
 * ) : WebAction {
 *
 *   @McpPost
 *   suspend fun handleMcpRequest(
 *     @RequestBody message: JSONRPCMessage,
 *     sendChannel: SendChannel<ServerSentEvent>
 *   ) {
 *     mcpStreamManager.withSseChannel(sendChannel) { handleMessage(message) }
 *   }
 * }
 * ```
 *
 * The server advertises the tools, resources and prompts capabilities when at least one of each kind is registered.
 * Every tool call records [McpMetrics.mcpToolHandlerLatency].
 *
 * @see misk.mcp.action.McpStreamManager For handling requests in web actions
 */
@ExperimentalMiskApi
class MiskMcpServer
internal constructor(
  val name: String,
  val version: String,
  val config: McpServerConfig,
  tools: Set<McpTool<*>>,
  resources: Set<McpResource>,
  resourceTemplates: Set<McpResourceTemplate>,
  prompts: Set<McpPrompt>,
  completionProviders: Set<McpCompletionProvider>,
  private val instructionsProvider: Provider<String>? = null,
  internal val mcpMetrics: McpMetrics,
  internal val requestStateCodec: RequestStateCodec? = null,
) {
  val serverInfo: Implementation =
    Implementation(
      name = name,
      version = version,
      title = config.title,
      websiteUrl = config.website_url,
      icons = config.icons.map { it.toIcon() }.ifEmpty { null },
    )

  val capabilities: ServerCapabilities =
    ServerCapabilities(
      tools = if (tools.isNotEmpty()) ServerCapabilities.Tools() else null,
      resources =
        if (resources.isNotEmpty() || resourceTemplates.isNotEmpty()) ServerCapabilities.Resources() else null,
      prompts = if (prompts.isNotEmpty()) ServerCapabilities.Prompts() else null,
      completions = if (completionProviders.isNotEmpty()) EmptyJsonObject else null,
    )

  val instructions: String?
    get() = instructionsProvider?.get()

  internal val tools: Map<String, RegisteredTool> =
    tools.sortedBy { it.name }.associate { it.name to RegisteredTool(it, it.toTool()) }

  internal val resources: Map<String, RegisteredResource> =
    resources.associate { it.uri to RegisteredResource(it, it.toResource()) }

  internal val resourceTemplates: List<RegisteredResourceTemplate> =
    resourceTemplates.map { RegisteredResourceTemplate(it, it.toResourceTemplate(), UriTemplate(it.uriTemplate)) }

  internal val prompts: Map<String, RegisteredPrompt> =
    prompts.associate { it.name to RegisteredPrompt(it, it.toPrompt()) }

  internal val completionProviders: Map<String, McpCompletionProvider> =
    completionProviders.associateBy { provider ->
      val ref = provider.ref
      val registered =
        when (ref) {
          is PromptReference -> ref.name in this.prompts
          is ResourceTemplateReference -> this.resourceTemplates.any { it.definition.uriTemplate == ref.uri }
        }
      require(registered) { "${provider::class.simpleName} completes ${ref.key}, which server $name doesn't serve" }
      ref.key
    }

  init {
    require(this.completionProviders.size == completionProviders.size) {
      "Server $name has more than one completion provider for the same prompt or resource template"
    }
  }

  internal val dispatcher = McpDispatcher(this)

  internal class RegisteredTool(val handler: McpTool<*>, val definition: Tool)

  internal class RegisteredResource(val handler: McpResource, val definition: Resource)

  internal class RegisteredResourceTemplate(
    val handler: McpResourceTemplate,
    val definition: ResourceTemplate,
    val uriTemplate: UriTemplate,
  )

  internal class RegisteredPrompt(val handler: McpPrompt, val definition: Prompt)
}

private fun McpTool<*>.toTool() =
  Tool(
    name = name,
    title = title,
    icons = icons,
    inputSchema = inputSchema,
    description = description,
    outputSchema = outputSchema,
    annotations =
      ToolAnnotations(
        title = title,
        readOnlyHint = readOnlyHint,
        destructiveHint = destructiveHint,
        idempotentHint = idempotentHint,
        openWorldHint = openWorldHint,
      ),
    meta = meta,
  )

private fun McpResource.toResource() =
  Resource(uri = uri, name = name, title = title, icons = icons, description = description, mimeType = mimeType)

private fun McpResourceTemplate.toResourceTemplate() =
  ResourceTemplate(
    uriTemplate = uriTemplate,
    name = name,
    title = title,
    icons = icons,
    description = description,
    mimeType = mimeType,
  )

private fun McpPrompt.toPrompt() =
  Prompt(name = name, title = title, icons = icons, description = description, arguments = arguments)

/** Identifies a prompt or resource template for completion. */
internal val Reference.key: String
  get() =
    when (this) {
      is PromptReference -> "$type:$name"
      is ResourceTemplateReference -> "$type:$uri"
    }

private fun McpIconConfig.toIcon() =
  Icon(
    src = src,
    mimeType = mime_type,
    sizes = sizes.ifEmpty { null },
    theme =
      theme?.let { theme ->
        requireNotNull(Icon.Theme.entries.find { it.name.equals(theme, ignoreCase = true) }) {
          "Unknown MCP icon theme '$theme': use light or dark"
        }
      },
  )
