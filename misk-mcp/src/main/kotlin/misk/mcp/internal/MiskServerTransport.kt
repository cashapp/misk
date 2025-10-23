package misk.mcp.internal

import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import misk.web.HttpCall

/**
 * Base class for Misk-specific MCP transport implementations.
 *
 * Extends the MCP Kotlin SDK's [AbstractTransport] to provide common functionality
 * for adapting Misk framework components to the pluggable transport system.
 * Concrete implementations handle different transport mechanisms (SSE, WebSocket).
 */
abstract class MiskServerTransport : AbstractTransport() {
  /** The HTTP call context for this transport session */
  abstract val call: HttpCall

  /** Unique identifier for this transport stream */
  abstract val streamId: String

  /** Handles incoming JSON-RPC messages from the client */
  abstract suspend fun handleMessage(message: JSONRPCMessage)
}