package misk.mcp.client.config

import misk.config.Config

/**
 * Configuration for MCP client connections.
 * 
 * Defines connection settings for multiple MCP servers that this
 * client will connect to.
 */
data class McpClientConfig @JvmOverloads constructor(
  /**
   * Map of server name to server configuration.
   * The server name is used as an identifier for dependency injection.
   */
  val servers: Map<String, McpServerConfig> = emptyMap()
) : java.util.LinkedHashMap<String, McpServerConfig>(servers), Config

/**
 * Configuration for a single MCP server connection.
 */
data class McpServerConfig @JvmOverloads constructor(
  /**
   * Transport configuration for connecting to the server.
   */
  val transport: McpTransport = McpTransport.StreamableHttp,

  val path: String = "/mcp",
  
  /**
   * Request timeout in milliseconds.
   * Default: 30 seconds
   */
  val timeout_ms: Long = 30_000,

)


enum class McpTransport {
  StreamableHttp,
  WebSocket,
}

