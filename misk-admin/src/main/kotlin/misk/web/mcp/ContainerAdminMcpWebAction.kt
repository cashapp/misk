@file:OptIn(ExperimentalMiskApi::class)

package misk.web.mcp

import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.channels.SendChannel
import misk.annotation.ExperimentalMiskApi
import misk.mcp.action.McpStreamManager
import misk.mcp.action.handleMessage
import misk.mcp.decode
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes
import misk.web.sse.ServerSentEvent

@Singleton
class ContainerAdminMcpWebAction
@Inject
constructor(@ContainerAdminMcp private val mcpStreamManager: McpStreamManager) : WebAction {
  @Post("/admin/mcp")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.SERVER_EVENT_STREAM)
  @AdminDashboardAccess
  suspend fun mcpPost(@RequestBody body: String, sendChannel: SendChannel<ServerSentEvent>) {
    val message = body.decode<JSONRPCMessage>()
    mcpStreamManager.withSseChannel(sendChannel) { handleMessage(message) }
  }
}
