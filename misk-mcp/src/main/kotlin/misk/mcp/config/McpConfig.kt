@file:Suppress("PropertyName")

package misk.mcp.config

import misk.annotation.ExperimentalMiskApi
import misk.config.Config

/**
 * Configuration for Model Context Protocol (MCP) servers.
 *
 * This configuration class manages multiple MCP server instances, where each server is identified by a unique name and
 * configured with its own [McpServerConfig].
 *
 * ## YAML Configuration Example
 *
 * ```yaml
 * mcp:
 *   my_server:
 *     version: "1.0.0"
 * ```
 *
 * @param servers Initial map of server name to [McpServerConfig]. Defaults to empty map.
 */
@ExperimentalMiskApi
class McpConfig @JvmOverloads constructor(servers: Map<String, McpServerConfig> = emptyMap()) :
  java.util.LinkedHashMap<String, McpServerConfig>(servers), Config

/**
 * Configuration for an individual MCP server instance.
 *
 * **Note**: Network configuration (port, hostname) is handled by the Misk web framework and is not part of this
 * MCP-specific configuration.
 *
 * ## YAML Configuration Example
 *
 * ```yaml
 * version: "1.0.0"
 * ```
 *
 * @param version The version string for this MCP server instance. This is not the protocol version reported to clients.
 *   It should follow semantic versioning (e.g., "1.0.0", "2.1.3-beta").
 * @param prompts Ignored. See [McpPromptConfig].
 * @param resources Ignored. See [McpResourceConfig].
 * @param tools Ignored. See [McpToolConfig].
 * @param enforce_strict_capabilities Ignored: the server sends no requests to clients.
 * @param allowed_origins Origins allowed to call the server, such as `https://app.example.com`. A request with any
 *   other `Origin` header gets a 403. Requests without an `Origin` header, such as those from non-browser clients, are
 *   always allowed. When empty, the server accepts every origin.
 * @param keep_alive_interval_ms How often to send an SSE comment while a handler runs. It keeps idle proxies from
 *   closing the stream, and it is how the server notices that the client went away and cancels the handler.
 */
data class McpServerConfig
@JvmOverloads
constructor(
  val version: String,
  val prompts: McpPromptConfig = McpPromptConfig(),
  val resources: McpResourceConfig = McpResourceConfig(),
  val tools: McpToolConfig = McpToolConfig(),
  @Deprecated("Ignored: the server sends no requests to clients") val enforce_strict_capabilities: Boolean = false,
  val allowed_origins: List<String> = emptyList(),
  val keep_alive_interval_ms: Long = 15_000,
) : Config

/**
 * Ignored: tools, prompts and resources are registered statically, so the server never sends list-changed
 * notifications.
 */
data class McpPromptConfig
@JvmOverloads
constructor(@Deprecated("Ignored: the prompt list never changes") val list_changed: Boolean = false) : Config

/** Ignored: the server supports neither resource subscriptions nor list-changed notifications. */
data class McpResourceConfig
@JvmOverloads
constructor(
  @Deprecated("Ignored: resource subscriptions are not supported") val subscribe: Boolean = false,
  @Deprecated("Ignored: the resource list never changes") val list_changed: Boolean = false,
) : Config

/** Ignored: tools are registered statically, so the server never sends list-changed notifications. */
data class McpToolConfig
@JvmOverloads
constructor(@Deprecated("Ignored: the tool list never changes") val list_changed: Boolean = false) : Config
