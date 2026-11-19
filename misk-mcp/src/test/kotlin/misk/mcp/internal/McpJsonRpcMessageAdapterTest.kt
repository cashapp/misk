package misk.mcp.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import misk.mcp.protocol.JSONRPCMessage
import misk.mcp.protocol.JSONRPCNotification
import misk.mcp.protocol.JSONRPCRequest
import misk.mcp.protocol.JSONRPCResponse

class McpJsonRpcMessageAdapterTest {
  @Test
  fun `decodes each kind of message`() {
    assertIs<JSONRPCRequest>(decode("""{"jsonrpc":"2.0","id":"1","method":"tools/list","params":{}}"""))
    assertIs<JSONRPCNotification>(decode("""{"jsonrpc":"2.0","method":"notifications/initialized"}"""))
    assertIs<JSONRPCResponse>(decode("""{"jsonrpc":"2.0","id":"1","result":{}}"""))
  }

  @Test
  fun `round-trips a message`() {
    val request = decode("""{"jsonrpc":"2.0","id":"1","method":"tools/list","params":{}}""")

    assertEquals(request, decode(McpJsonRpcMessageAdapter.toJson(request)))
  }

  private fun decode(json: String): JSONRPCMessage = McpJsonRpcMessageAdapter.fromJson(json)!!
}
