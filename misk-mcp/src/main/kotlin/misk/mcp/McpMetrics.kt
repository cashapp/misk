package misk.mcp

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.metrics.v2.Metrics
import kotlin.time.Duration

@Singleton
internal class McpMetrics @Inject internal constructor(metrics: Metrics) {
  private val mcpToolHandlerLatency = metrics.histogram(
    "mcp_tool_handler_latency",
    "how long a tool's handler() method took",
    listOf("server_name", "tool_name", "tool_outcome")
  )
  
  fun mcpToolHandlerLatency(duration: Duration, serverName: String, toolName: String, outcome: ToolCallOutcome) {
    mcpToolHandlerLatency
      .labels(serverName, toolName, outcome.name)
      .observe(duration.inWholeMilliseconds.toDouble())
  }
  
  enum class ToolCallOutcome {
    Success,
    Error,
    Exception,
  }
}
