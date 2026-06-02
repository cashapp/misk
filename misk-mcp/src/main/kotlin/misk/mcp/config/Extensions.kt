package misk.mcp.config

import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities.Prompts
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities.Resources
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities.Tools

/**
 * Extension functions that convert Misk MCP configuration objects to MCP server ServerCapabilities.
 *
 * These functions provide a simple mapping between the Misk configuration format and the standard MCP (Model Context
 * Protocol) server capability objects.
 */

/** Converts [McpPromptConfig] to MCP server [Prompts] capability. */
internal fun McpPromptConfig.asPrompts() = Prompts(listChanged = list_changed)

/** Converts [McpResourceConfig] to MCP server [Resources] capability. */
internal fun McpResourceConfig.asResources() = Resources(subscribe = subscribe, listChanged = list_changed)

/** Converts [McpToolConfig] to MCP server [Tools] capability. */
internal fun McpToolConfig.asTools() = Tools(listChanged = list_changed)
