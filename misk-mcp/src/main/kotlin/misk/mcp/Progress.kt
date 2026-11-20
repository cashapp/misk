package misk.mcp

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.json.jsonObject
import misk.annotation.ExperimentalMiskApi
import misk.mcp.internal.McpRequestContext
import misk.mcp.protocol.JSONRPCNotification
import misk.mcp.protocol.McpMethod
import misk.mcp.protocol.ProgressNotificationParams

/**
 * Tells the client how far the current tool call, prompt or resource read has got, with a `notifications/progress` on
 * the request's SSE stream. [progress] must grow with each call, and [total] is the value it will reach, if known.
 *
 * Does nothing when the client didn't ask for progress with a `_meta.progressToken`, or outside an MCP handler.
 */
@ExperimentalMiskApi
suspend fun reportProgress(progress: Double, total: Double? = null, message: String? = null) {
  val context = currentCoroutineContext()[McpRequestContext] ?: return
  val token = context.meta?.progressToken ?: return
  val params = ProgressNotificationParams(progressToken = token, progress = progress, total = total, message = message)
  context.stream.send(
    JSONRPCNotification(
      method = McpMethod.NOTIFICATIONS_PROGRESS,
      params = McpJson.encodeToJsonElement(ProgressNotificationParams.serializer(), params).jsonObject,
    )
  )
}
