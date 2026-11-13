package misk.mcp

import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotification
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotificationParams
import kotlinx.coroutines.currentCoroutineContext
import misk.annotation.ExperimentalMiskApi
import misk.mcp.action.currentClientConnection
import misk.mcp.internal.McpProgressToken

/**
 * Sends a `notifications/progress` update to the client for the tool call currently being handled.
 *
 * The update is tied to the call through the `progressToken` the client sent in the request's `_meta`. When the client
 * did not send a token, this does nothing. Call it from within an [McpTool] handler:
 * ```kotlin
 * override suspend fun handle(input: ImportInput): ToolResult {
 *   input.files.forEachIndexed { index, file ->
 *     importer.import(file)
 *     reportProgress(progress = index + 1.0, total = input.files.size.toDouble(), message = "Imported ${file.name}")
 *   }
 *   return ToolResult(TextContent("Imported ${input.files.size} files"))
 * }
 * ```
 *
 * @param progress Progress so far. It must increase with each call, even if [total] is unknown.
 * @param total Total amount of work, if known.
 * @param message Optional human-readable description of the current progress.
 * @throws IllegalStateException if called outside a tool handler
 */
@ExperimentalMiskApi
suspend fun reportProgress(progress: Double, total: Double? = null, message: String? = null) {
  val progressToken = currentCoroutineContext()[McpProgressToken]?.progressToken ?: return
  currentClientConnection()
    .notification(
      ProgressNotification(
        ProgressNotificationParams(progressToken = progressToken, progress = progress, total = total, message = message)
      )
    )
}
