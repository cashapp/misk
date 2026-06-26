package misk.mcp.client

import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport


class McpClientProvider (
  val implementation: Implementation,
  val options: ClientOptions,
  private val transport: AbstractTransport
) {
  suspend fun connect(): McpClientConnection =
    McpClientConnection(this).also {
      it.connect(transport)
    }
}