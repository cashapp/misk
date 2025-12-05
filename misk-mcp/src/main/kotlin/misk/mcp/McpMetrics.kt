package misk.mcp

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.metrics.v2.Metrics
import kotlin.time.Duration

/**
 * Metrics collection for Model Context Protocol (MCP) server operations.
 *
 * This class provides observability into MCP server performance and tool execution behavior
 * by collecting and reporting metrics through the Misk metrics system. All metrics are
 * automatically collected by [MiskMcpServer] without requiring any additional configuration
 * from tool implementations.
 *
 * ## Collected Metrics
 *
 * ### Tool Handler Latency (`mcp_tool_handler_latency`)
 * A histogram metric that measures the execution time of MCP tool handlers in milliseconds.
 * This metric helps identify performance bottlenecks and monitor tool execution patterns.
 *
 * **Labels:**
 * - `server_name`: The name of the MCP server instance (from [MiskMcpServer.name])
 * - `server_version`: The version of the MCP server instance (from [MiskMcpServer.version])
 * - `tool_name`: The name of the executed tool (from [McpTool.name])
 * - `tool_outcome`: The execution outcome - one of [ToolCallOutcome] values
 *
 * **Outcomes:**
 * - [ToolCallOutcome.Success]: Tool executed successfully and returned a result
 * - [ToolCallOutcome.Error]: Tool executed but returned an error result (isError=true)
 * - [ToolCallOutcome.Exception]: Tool execution threw an unhandled exception
 *
 * ## Usage
 *
 * Metrics are automatically collected by [MiskMcpServer] for all registered tools.
 * No manual instrumentation is required in tool implementations:
 *
 * ```kotlin
 * @Singleton
 * class MyTool : McpTool<MyInput> {
 *   override val name = "my-tool"
 *
 *   override suspend fun handler(request: CallToolRequest): CallToolResult {
 *     // This execution is automatically timed and reported
 *     return CallToolResult(content = listOf(TextContent("result")))
 *   }
 * }
 * ```
 *
 * ## Monitoring
 *
 * The collected metrics can be used to:
 * - Monitor tool performance and identify slow operations
 * - Track error rates and failure patterns across different tools
 * - Set up alerting for tool execution anomalies
 * - Analyze usage patterns and optimize frequently-used tools
 *
 * Example Prometheus queries:
 * ```
 * # Average tool execution time by tool
 * rate(mcp_tool_handler_latency_sum[5m]) / rate(mcp_tool_handler_latency_count[5m])
 *
 * # Tool error rate
 * rate(mcp_tool_handler_latency_count{tool_outcome!="Success"}[5m]) / rate(mcp_tool_handler_latency_count[5m])
 *
 * # 95th percentile tool latency
 * histogram_quantile(0.95, rate(mcp_tool_handler_latency_bucket[5m]))
 * ```
 *
 * @see MiskMcpServer For automatic metrics integration
 * @see McpTool For tool implementation interface
 */
@Singleton
internal class McpMetrics @Inject internal constructor(metrics: Metrics) {
  private val mcpToolHandlerLatency = metrics.histogram(
    "mcp_tool_handler_latency",
    "how long a tool's handler() method took",
    listOf("server_name", "server_version", "tool_name", "tool_outcome")
  )

  /**
   * Records the execution time and outcome of an MCP tool handler.
   *
   * This method is automatically called by [MiskMcpServer] for every tool execution
   * and should not be called directly by tool implementations.
   *
   * @param duration The time taken to execute the tool handler
   * @param serverName The name of the MCP server instance
   * @param version The version of the MCP server instance
   * @param toolName The name of the executed tool
   * @param outcome The execution outcome (Success, Error, or Exception)
   */
  fun mcpToolHandlerLatency(
    duration: Duration,
    serverName: String,
    version: String,
    toolName: String,
    outcome: ToolCallOutcome
  ) {
    mcpToolHandlerLatency
      .labels(serverName, version, toolName, outcome.name)
      .observe(duration.inWholeMilliseconds.toDouble())
  }

  /**
   * Represents the possible outcomes of MCP tool execution for metrics reporting.
   *
   * These outcomes help categorize tool executions for monitoring and alerting:
   * - Use [Success] and [Error] rates to monitor business logic health
   * - Use [Exception] rate to monitor system-level issues and bugs
   */
  enum class ToolCallOutcome {
    /** Tool executed successfully and returned a valid result */
    Success,

    /** Tool executed but returned an error result (CallToolResult.isError = true) */
    Error,

    /** Tool execution threw an unhandled exception */
    Exception,
  }
}
