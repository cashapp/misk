package misk.mcp.internal

internal const val WEBSOCKET_DEPRECATION =
  "WebSocket is not an MCP transport. It will be removed when misk-mcp adopts MCP 2026-07-28. " +
    "Use @McpPost (Streamable HTTP) instead."

internal const val SESSION_DEPRECATION =
  "MCP 2026-07-28 removes sessions and the Mcp-Session-Id header. This will be removed when misk-mcp adopts that " +
    "spec. Pass any state a tool needs across calls as an explicit tool argument instead."
