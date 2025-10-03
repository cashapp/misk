package misk.mcp

import com.google.inject.Provider
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.Method
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import misk.annotation.ExperimentalMiskApi
import misk.exceptions.BadRequestException
import misk.exceptions.NotFoundException
import misk.exceptions.WebActionException
import misk.logging.getLogger
import misk.mcp.action.SESSION_ID_HEADER
import misk.mcp.config.McpServerConfig
import misk.mcp.config.asPrompts
import misk.mcp.config.asResources
import misk.mcp.config.asTools
import misk.mcp.internal.MiskServerTransport

/**
 * Misk implementation of a Model Context Protocol (MCP) server.
 *
 * This class extends the MCP Kotlin SDK's [Server] class to provide MCP server capabilities
 * within the Misk framework. It handles both client-to-server events (tool calls, resource reads,
 * prompt requests) and server-to-client events (resource update notifications, logging).
 *
 * The Misk dependency injection system automatically configures the server with registered tools,
 * resources, and prompts, and the server exposes them to MCP clients through JSON-RPC 2.0
 * messages over Server-Sent Events (SSE).
 *
 * ## Key Features
 *
 * - **Tool Execution**: Handles client requests to execute registered [McpTool] implementations
 * - **Resource Access**: Serves registered [McpResource] content to clients
 * - **Prompt Templates**: Provides registered [McpPrompt] templates for client use
 * - **Capability Negotiation**: The server automatically configures capabilities based on registered components
 * - **Session Management**: Manages multiple concurrent client sessions through SSE connections
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
 *
 * ```kotlin
 * @Singleton
 * class MyMcpWebAction @Inject constructor(
 *   private val mcpStreamManager: McpStreamManager
 * ) : WebAction {
 *
 *   @McpPost
 *   suspend fun handleMcpRequest(
 *     @RequestBody message: JSONRPCMessage,
 *     sendChannel: SendChannel<ServerSentEvent>
 *   ) {
 *     mcpStreamManager.withResponseChannel(sendChannel) {
 *       // 'this' is MiskMcpServer - handle the client message
 *       handleMessage(message)
 *     }
 *   }
 * }
 * ```
 *
 * ## Configuration
 *
 * The server automatically determines capabilities from the [McpServerConfig] and registered
 * components:
 * - The server enables tools capability if any [McpTool] implementations are registered
 * - The server enables resources capability if any [McpResource] implementations are registered
 * - The server enables prompts capability if any [McpPrompt] implementations are registered
 *
 * @param name The unique name identifier for this MCP server instance
 * @param config Configuration settings including version and capability flags
 * @param tools Set of tool implementations to register with the server
 * @param resources Set of resource implementations to register with the server
 * @param prompts Set of prompt implementations to register with the server
 *
 * @see misk.mcp.action.McpStreamManager For managing SSE streams and server lifecycle
 * @see McpTool For implementing executable tools
 * @see McpResource For implementing accessible resources
 * @see McpPrompt For implementing prompt templates
 */
@ExperimentalMiskApi
class MiskMcpServer internal constructor(
  val name: String,
  val config: McpServerConfig,
  private val mcpSessionHandler: McpSessionHandler?,
  tools: Set<McpTool<*>>,
  resources: Set<McpResource>,
  prompts: Set<McpPrompt>,
  instructionsProvider: Provider<String>? = null,
) : Server(
  Implementation(
    name = name,
    version = config.version,
  ),
  ServerOptions(
    capabilities = ServerCapabilities(
      experimental = null,
      sampling = null,
      logging = null,
      prompts = if (prompts.isNotEmpty()) config.prompts.asPrompts() else null,
      resources = if (resources.isNotEmpty()) config.resources.asResources() else null,
      tools = if (tools.isNotEmpty()) config.tools.asTools() else null,
    ),
  ),
  // TODO: propagate instructionsProvider once supported in MCP SDK
) {

  init {
    onInitialized {
      logger.debug { "MCP server $name initialized" }
    }

    prompts.forEach { prompt ->
      addPrompt(
        name = prompt.name,
        description = prompt.description,
        arguments = prompt.arguments,
        promptProvider = prompt::handler,
      )
    }

    resources.forEach { resource ->
      addResource(
        uri = resource.uri,
        name = resource.name,
        description = resource.description,
        mimeType = resource.mimeType,
        readHandler = resource::handler,
      )
    }

    tools.forEach { tool ->
      addTool(
        name = tool.name,
        description = tool.description,
        inputSchema = tool.inputSchema,
        outputSchema = tool.outputSchema,
        toolAnnotations = ToolAnnotations(
          title = tool.title,
          readOnlyHint = tool.readOnlyHint,
          destructiveHint = tool.destructiveHint,
          idempotentHint = tool.idempotentHint,
          openWorldHint = tool.openWorldHint,
        ),
        handler = tool::handler,
      )
    }
  }

  @JvmOverloads
  suspend fun handleMessage(message: JSONRPCMessage, sessionId: String? = null) {
    val miskServerTransport = checkNotNull((transport as? MiskServerTransport)) {
      "MiskMcpServer requires a connected MiskServerTransport to handle messages"
    }
    if (mcpSessionHandler != null) {
      if (message is JSONRPCRequest) {
        if (message.method == Method.Defined.Initialize.value) {
          // On an initialization request, initialize a new session for the client and return
          // in the SESSION_ID_HEADER response header
          val sessionId = mcpSessionHandler.initialize()
          miskServerTransport.stream.call.setResponseHeader(SESSION_ID_HEADER, sessionId)
        } else {
          // On non-initialization requests, validate that the session ID exists in the request
          // and that it's a valid, active session
          val sessionId = miskServerTransport.stream.call.requestHeaders[SESSION_ID_HEADER]
            ?: throw BadRequestException("Missing required $SESSION_ID_HEADER header")
          if (!mcpSessionHandler.isActive(sessionId)) {
            throw NotFoundException("SessionID $SESSION_ID_HEADER does not exist")
          }
        }
      }
    }

    miskServerTransport.handleMessage(message, sessionId)

    when (message) {
      is JSONRPCNotification,
      is JSONRPCResponse -> {
        // Notifications and responses should return a 202 if handled successfully with no content
        // Because we default to a SSE response and a server session, we need to end the session and directly
        // return the result. If the handler fails to handle the response or notification, it should throw an
        // error that should be translated to a JSON-RPC error response
        throw AcceptedResponseException()
      }

      else -> Unit
    }
  }

  companion object {
    private val logger = getLogger<MiskMcpServer>()
  }
}

/** Represents a 202 Accepted response to indicate a notification or response was handled successfully */
internal class AcceptedResponseException : WebActionException(202, "Accepted")

