@file:Suppress("PropertyName")

package misk.mcp.config

import misk.annotation.ExperimentalMiskApi
import wisp.config.Config

/**
 * Configuration for Model Context Protocol (MCP) servers.
 *
 * This configuration class manages multiple MCP server instances, where each server
 * is identified by a unique name and configured with its own [McpServerConfig].
 *
 * **Note**: The current implementation supports only a single MCP server instance.
 * Multiple server configurations will result in an error during module initialization.
 *
 * ## YAML Configuration Example
 *
 * ```yaml
 * mcp:
 *   my_server:
 *     version: "1.0.0"
 *     prompts:
 *       list_changed: false
 *     resources:
 *       subscribe: true
 *       list_changed: false
 *     tools:
 *       list_changed: false
 * ```
 *
 * @param servers Initial map of server name to [McpServerConfig]. Defaults to empty map.
 */
@ExperimentalMiskApi
class McpConfig @JvmOverloads constructor(
  servers: Map<String, McpServerConfig> = emptyMap()
) : java.util.LinkedHashMap<String, McpServerConfig>(servers), Config

/**
 * Configuration for an individual MCP server instance.
 *
 * Defines the capabilities and feature configurations for a single MCP server.
 * Each server can expose prompts, resources, and tools to MCP clients.
 *
 * **Note**: Network configuration (port, hostname) is handled by the Misk web framework
 * and is not part of this MCP-specific configuration.
 *
 * ## YAML Configuration Example
 *
 * ```yaml
 * version: "1.0.0"
 * prompts:
 *   list_changed: false
 * resources:
 *   subscribe: true
 *   list_changed: false
 * tools:
 *   list_changed: false
 * ```
 *
 * @param version The version string for this MCP server instance. This is not the protocol
 *                version reported to clients during the initialization handshake. It should
 *                follow semantic versioning (e.g., "1.0.0", "2.1.3-beta").
 * @param prompts Configuration for prompt-related capabilities. See [McpPromptConfig].
 * @param resources Configuration for resource-related capabilities. See [McpResourceConfig].
 * @param tools Configuration for tool-related capabilities. See [McpToolConfig].
 */
data class McpServerConfig @JvmOverloads constructor(
  val version: String,
  val prompts: McpPromptConfig = McpPromptConfig(),
  val resources: McpResourceConfig = McpResourceConfig(),
  val tools: McpToolConfig = McpToolConfig(),
) : Config

/**
 * Configuration for MCP prompt capabilities.
 *
 * Prompts in MCP are reusable templates or instructions that can be invoked by clients.
 * This configuration controls how the server handles prompt-related operations.
 *
 * ## YAML Configuration Example
 *
 * ```yaml
 * prompts:
 *   list_changed: false
 * ```
 *
 * @param list_changed Whether the server supports dynamic registration/deregistration of prompts.
 *                     **Defaults to false** as dynamic registration is not supported in the current
 *                     implementation. This feature would be more applicable to a plugin architecture
 *                     where prompts can be added or removed at runtime. When false, the prompt list
 *                     is considered static after server initialization.
 */
data class McpPromptConfig @JvmOverloads constructor(
  val list_changed: Boolean = false,
) : Config

/**
 * Configuration for MCP resource capabilities.
 *
 * Resources in MCP represent data or content that can be accessed by clients, such as
 * files, database records, or API endpoints. This configuration controls how the server
 * handles resource-related operations and notifications.
 *
 * ## YAML Configuration Examples
 *
 * ### Basic Configuration (No Subscriptions)
 * ```yaml
 * resources:
 *   subscribe: false
 *   list_changed: false
 * ```
 *
 * ### With Resource Update Notifications
 * ```yaml
 * resources:
 *   subscribe: true
 *   list_changed: false
 * ```
 *
 * @param subscribe Whether the server supports resource update subscriptions. When enabled (true),
 *                  clients can subscribe to receive notifications when resources are updated.
 *                  **Note**: The current implementation supports the subscription capability but
 *                  resource update notification methods are not yet exposed in the Misk wrapper.
 *                  This is useful for real-time data synchronization when fully implemented.
 *                  Defaults to false.
 * @param list_changed Whether the server supports dynamic registration/deregistration of resources.
 *                     **Defaults to false** as dynamic registration is not supported in the current
 *                     implementation. This feature would be more applicable to a plugin architecture
 *                     where resources can be added or removed at runtime. When false, the resource
 *                     list is considered static after server initialization.
 */
data class McpResourceConfig @JvmOverloads constructor(
  val subscribe: Boolean = false,
  val list_changed: Boolean = false,
) : Config

/**
 * Configuration for MCP tool capabilities.
 *
 * Tools in MCP are executable functions that clients can invoke to perform specific
 * operations or computations. This configuration controls how the server handles
 * tool-related operations.
 *
 * ## YAML Configuration Example
 *
 * ```yaml
 * tools:
 *   list_changed: false
 * ```
 *
 * @param list_changed Whether the server supports dynamic registration/deregistration of tools.
 *                     **Defaults to false** as dynamic registration is not supported in the current
 *                     implementation. This feature would be more applicable to a plugin architecture
 *                     where tools can be added or removed at runtime. When false, the tool list
 *                     is considered static after server initialization.
 */
data class McpToolConfig @JvmOverloads constructor(
  val list_changed: Boolean = false,
) : Config
