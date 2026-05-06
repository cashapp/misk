package misk.mcp

import io.modelcontextprotocol.kotlin.sdk.types.RequestMeta
import misk.annotation.ExperimentalMiskApi

/**
 * Bridge base class for plain [McpTool] authors that need access to the request's `_meta`.
 *
 * Subclasses implement only the meta-aware [handle] overload — the input-only `handle(input)` is
 * sealed by this class and delegates to the meta-aware overload with `meta = null`, so direct
 * callers (test helpers, etc.) of the no-meta overload continue to work without re-implementing
 * the body.
 *
 * The framework's dispatcher branches on `tool is MetaAwareTool<*, *>` at invocation time, so
 * mixing this base in is the only opt-in step needed — no explicit annotations or registrations.
 *
 * ## Example
 *
 * ```kotlin
 * @Singleton
 * class ProgressTrackingTool @Inject constructor() : MetaAwareMcpTool<MyInput>() {
 *   override val name = "progress_tracking"
 *   override val description = "..."
 *
 *   override suspend fun handle(input: MyInput, meta: RequestMeta?): ToolResult {
 *     val progressToken = meta?.progressToken
 *     // ...
 *     return ToolResult(TextContent("done"))
 *   }
 * }
 * ```
 *
 * @param I The input type for this tool.
 * @see MetaAwareTool
 * @see MetaAwareStructuredMcpTool
 */
@ExperimentalMiskApi
abstract class MetaAwareMcpTool<I : Any> : McpTool<I>(), MetaAwareTool<I, McpTool.ToolResult> {
  abstract override suspend fun handle(input: I, meta: RequestMeta?): ToolResult

  final override suspend fun handle(input: I): ToolResult = handle(input, null)
}
