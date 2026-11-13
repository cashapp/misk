package misk.mcp

import com.google.inject.Provider
import io.modelcontextprotocol.kotlin.sdk.server.ClientConnection
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredResource
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.CompleteRequest
import io.modelcontextprotocol.kotlin.sdk.types.CompleteResult
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.Prompt
import io.modelcontextprotocol.kotlin.sdk.types.PromptReference
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.Resource
import io.modelcontextprotocol.kotlin.sdk.types.ResourceTemplate
import io.modelcontextprotocol.kotlin.sdk.types.ResourceTemplateReference
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import misk.annotation.ExperimentalMiskApi
import misk.mcp.config.McpServerConfig
import misk.mcp.config.asIcon
import misk.mcp.config.asPrompts
import misk.mcp.config.asResources
import misk.mcp.config.asTools
import misk.mcp.internal.McpClientConnection
import misk.mcp.internal.McpProgressToken
import misk.mcp.internal.McpServerSession
import misk.mcp.internal.build

/**
 * Misk implementation of a Model Context Protocol (MCP) server.
 *
 * This class extends the MCP Kotlin SDK's [Server] class to provide MCP server capabilities within the Misk framework.
 * It handles both client-to-server events (tool calls, resource reads, prompt requests) and server-to-client events
 * (resource update notifications, logging).
 *
 * The Misk dependency injection system automatically configures the server with registered tools, resources, and
 * prompts, and the server exposes them to MCP clients through JSON-RPC 2.0 messages over Server-Sent Events (SSE).
 *
 * ## Key Features
 * - **Tool Execution**: Handles client requests to execute registered [McpTool] implementations
 * - **Resource Access**: Serves registered [McpResource] content to clients
 * - **Prompt Templates**: Provides registered [McpPrompt] templates for client use
 * - **Capability Negotiation**: The server automatically configures capabilities based on registered components
 * - **Session Management**: Manages multiple concurrent client sessions through SSE connections
 * - **Metrics Integration**: Automatically wires up all registered tools to [McpMetrics] for monitoring tool execution
 *   latency and outcomes
 *
 * ## Client-to-Server Events
 *
 * The server handles these incoming client requests:
 * - `tools/call` - Execute a registered tool with provided arguments
 * - `resources/read` - Read content from a registered resource
 * - `prompts/get` - Retrieve a prompt template with optional arguments
 * - `resources/subscribe` - Subscribe to resource update notifications (if enabled)
 *
 * ## Server-to-Client Events
 *
 * The server can send these events to clients:
 * - `resources/updated` - Notify clients of resource changes (when subscriptions are enabled)
 * - `logging` - Send log messages to clients for debugging
 *
 * ## Usage
 *
 * [McpServerModule] typically creates and manages the server, and web actions access it through
 * [misk.mcp.action.McpStreamManager]:
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
 *     mcpStreamManager.withSseChannel(sendChannel) {
 *       // 'this' is MiskMcpServer - handle the client message
 *       handleMessage(message)
 *     }
 *   }
 * }
 * ```
 *
 * ## Configuration
 *
 * The server automatically determines capabilities from the [McpServerConfig] and registered components:
 * - The server enables tools capability if any [McpTool] implementations are registered
 * - The server enables resources capability if any [McpResource] or [McpResourceTemplate] implementations are
 *   registered
 * - The server enables prompts capability if any [McpPrompt] implementations are registered
 *
 * @param name The unique name identifier for this MCP server instance
 * @param config Configuration settings including version and capability flags
 * @param tools Set of tool implementations to register with the server
 * @param resources Set of resource implementations to register with the server
 * @param resourceTemplates Set of resource template implementations to register with the server
 * @param prompts Set of prompt implementations to register with the server
 * @param instructionsProvider Optional provider for server instructions text
 * @see misk.mcp.action.McpStreamManager For managing SSE streams and server lifecycle
 * @see McpTool For implementing executable tools
 * @see McpResource For implementing accessible resources
 * @see McpResourceTemplate For implementing parameterized resource templates
 * @see McpPrompt For implementing prompt templates
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
  instructionsProvider: Provider<String>? = null,
  private val mcpMetrics: McpMetrics,
) :
  Server(
    Implementation(
      name = name,
      version = version,
      title = config.title,
      websiteUrl = config.website_url,
      icons = config.icons.map { it.asIcon() }.ifEmpty { null },
    ),
    ServerOptions(
      capabilities =
        ServerCapabilities(
          experimental = null,
          completions =
            if (prompts.any { it is McpCompletionProvider } || resourceTemplates.any { it is McpCompletionProvider }) {
              EmptyJsonObject
            } else {
              null
            },
          logging = null,
          prompts = if (prompts.isNotEmpty()) config.prompts.asPrompts() else null,
          resources =
            if (resources.isNotEmpty() || resourceTemplates.isNotEmpty()) config.resources.asResources() else null,
          tools = if (tools.isNotEmpty()) config.tools.asTools() else null,
        ),
      enforceStrictCapabilities = config.enforce_strict_capabilities,
      // Tool handlers commonly block, so keep post-initialization handlers off Dispatchers.Default.
      handlerCoroutineContext = Dispatchers.IO,
    ),
    instructionsProvider = instructionsProvider?.let { { it.get() } },
  ) {

  private val promptCompletions: Map<String, McpCompletionProvider?> =
    prompts.associate { it.name to it as? McpCompletionProvider }

  private val resourceTemplateCompletions: Map<String, McpCompletionProvider?> =
    resourceTemplates.associate { it.uriTemplate to it as? McpCompletionProvider }

  init {
    prompts.forEach { prompt ->
      addPrompt(
        prompt =
          Prompt(
            name = prompt.name,
            description = prompt.description,
            arguments = prompt.arguments,
            title = prompt.title,
            icons = prompt.icons,
          ),
        promptProvider = { request -> withContext(handlerContext()) { prompt.handler(request) } },
      )
    }

    // addResources requires the resources capability even for an empty list.
    if (resources.isNotEmpty())
      addResources(
        resources.map { resource ->
          RegisteredResource(
            resource =
              Resource(
                uri = resource.uri,
                name = resource.name,
                description = resource.description,
                mimeType = resource.mimeType,
                title = resource.title,
                icons = resource.icons,
              ),
            readHandler = { request -> withContext(handlerContext()) { resource.handler(request) } },
          )
        }
      )

    resourceTemplates.forEach { template ->
      addResourceTemplate(
        template =
          ResourceTemplate(
            uriTemplate = template.uriTemplate,
            name = template.name,
            description = template.description,
            mimeType = template.mimeType,
            title = template.title,
            icons = template.icons,
          ),
        readHandler = { request, variables -> withContext(handlerContext()) { template.handler(request, variables) } },
      )
    }

    tools.forEach { tool ->
      addTool(
        tool =
          Tool.build(tool.name, tool.inputSchema) {
            description = tool.description
            title = tool.title
            icons = tool.icons
            tool.outputSchema?.let { outputSchema = it }
            annotations =
              ToolAnnotations.build {
                title = tool.title
                readOnlyHint = tool.readOnlyHint
                destructiveHint = tool.destructiveHint
                idempotentHint = tool.idempotentHint
                openWorldHint = tool.openWorldHint
              }
            tool.meta?.let { meta = it }
          },
        handler = metricReportingHandler(tool.name, tool::handler),
      )
    }
  }

  /** The SDK has no server-side `completion/complete` handler, so each session needs one installed. */
  internal fun ServerSession.installCompletionHandler() {
    if (promptCompletions.values.none { it != null } && resourceTemplateCompletions.values.none { it != null }) return
    setRequestHandler<CompleteRequest>(Method.Defined.CompletionComplete) { request, _ ->
      withContext(McpServerSession(this)) { complete(request) }
    }
  }

  private suspend fun complete(request: CompleteRequest): CompleteResult {
    val provider =
      when (val ref = request.ref) {
        is PromptReference ->
          promptCompletions.getOrElse(ref.name) { throw invalidParams("Prompt not found: ${ref.name}") }
        is ResourceTemplateReference ->
          resourceTemplateCompletions.getOrElse(ref.uri) {
            throw invalidParams("Resource template not found: ${ref.uri}")
          }
        else -> throw invalidParams("Unsupported completion reference: $ref")
      }
    val values =
      provider
        ?.complete(request.argument.name, request.argument.value, request.params.context?.arguments.orEmpty())
        .orEmpty()
    return CompleteResult(
      CompleteResult.Completion(
        values = values.take(McpCompletionProvider.MAX_COMPLETION_VALUES),
        total = values.size,
        hasMore = values.size > McpCompletionProvider.MAX_COMPLETION_VALUES,
      )
    )
  }

  private fun invalidParams(message: String) = McpException(RPCError.ErrorCode.INVALID_PARAMS, message)

  // Handlers run on the SDK's handler scope once a session is initialized, so session context is re-installed here.
  private fun ClientConnection.handlerContext(): CoroutineContext =
    McpClientConnection(this) + (sessions[sessionId]?.let(::McpServerSession) ?: EmptyCoroutineContext)

  private fun CallToolRequest.progressTokenContext(): CoroutineContext =
    meta?.progressToken?.let(::McpProgressToken) ?: EmptyCoroutineContext

  private fun metricReportingHandler(
    toolName: String,
    handler: suspend (CallToolRequest) -> CallToolResult,
  ): suspend ClientConnection.(CallToolRequest) -> CallToolResult {
    return { request ->
      val mark = TimeSource.Monotonic.markNow()
      var outcome = McpMetrics.ToolCallOutcome.Success

      try {
        withContext(handlerContext() + request.progressTokenContext()) { handler(request) }
          .also { result ->
            outcome =
              if (result.isError == true) McpMetrics.ToolCallOutcome.Error else McpMetrics.ToolCallOutcome.Success
          }
      } catch (ex: Exception) {
        outcome = McpMetrics.ToolCallOutcome.Exception
        throw ex
      } finally {
        val duration = mark.elapsedNow()
        mcpMetrics.mcpToolHandlerLatency(duration, name, version, toolName, outcome)
      }
    }
  }
}
