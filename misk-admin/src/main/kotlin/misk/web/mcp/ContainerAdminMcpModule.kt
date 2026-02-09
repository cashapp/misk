@file:OptIn(ExperimentalMiskApi::class)

package misk.web.mcp

import misk.annotation.ExperimentalMiskApi
import misk.inject.KAbstractModule
import misk.mcp.McpPromptModule
import misk.mcp.McpResourceModule
import misk.mcp.McpServerModule
import misk.mcp.McpToolModule
import misk.mcp.config.McpConfig
import misk.mcp.config.McpServerConfig
import misk.web.WebActionModule

/**
 * Installs an MCP server at `/admin/mcp` that exposes all container admin metadata as MCP tools, resources, and
 * prompts.
 *
 * This provides AI/LLM agents with structured access to the same metadata available through the admin dashboard and
 * REST API (AllMetadataAction), including config, JVM stats, web actions, Guice bindings, service graph, and database
 * metadata.
 */
class ContainerAdminMcpModule : KAbstractModule() {
  override fun configure() {
    val mcpConfig = McpConfig(mapOf(SERVER_NAME to McpServerConfig(version = "1.0.0")))
    install(McpServerModule.create<ContainerAdminMcp>(SERVER_NAME, mcpConfig))

    // Web action at /admin/mcp
    install(WebActionModule.create<ContainerAdminMcpWebAction>())

    // Tool: get_metadata - query any metadata by ID
    install(McpToolModule.create<ContainerAdminMcp, ContainerMetadataTool>())

    // Resources: each metadata type as addressable MCP content
    install(McpResourceModule.create<ContainerAdminMcp, ConfigMcpResource>())
    install(McpResourceModule.create<ContainerAdminMcp, JvmMcpResource>())
    install(McpResourceModule.create<ContainerAdminMcp, WebActionsMcpResource>())
    install(McpResourceModule.create<ContainerAdminMcp, GuiceMcpResource>())
    install(McpResourceModule.create<ContainerAdminMcp, ServiceGraphMcpResource>())
    install(McpResourceModule.create<ContainerAdminMcp, DatabaseMcpResource>())

    // Prompt: container debugging/inspection guide
    install(McpPromptModule.create<ContainerAdminMcp, ContainerAdminPrompt>())
  }

  companion object {
    private const val SERVER_NAME = "container-admin"
  }
}
