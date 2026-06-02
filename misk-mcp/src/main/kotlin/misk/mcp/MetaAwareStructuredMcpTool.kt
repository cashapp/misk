package misk.mcp

import io.modelcontextprotocol.kotlin.sdk.types.RequestMeta
import misk.annotation.ExperimentalMiskApi

/**
 * Bridge base class for [StructuredMcpTool] authors that need access to the request's `_meta`.
 *
 * Subclasses implement only the meta-aware [handle] overload — the input-only `handle(input)` is sealed by this class
 * and delegates to the meta-aware overload with `meta = null`. Returns [McpTool.ToolResult] (rather than
 * `StructuredToolResult<O>` directly) because `StructuredMcpTool.handle` does not narrow the return type —
 * implementations build their typed result via the inherited `ToolResult(result: O, ...)` factories, which already
 * produce a `StructuredToolResult<O>` typed as `ToolResult`.
 *
 * @param I The input type for this tool.
 * @param O The output type for this tool.
 * @see MetaAwareTool
 * @see MetaAwareMcpTool
 */
@ExperimentalMiskApi
abstract class MetaAwareStructuredMcpTool<I : Any, O : Any> :
  StructuredMcpTool<I, O>(), MetaAwareTool<I, McpTool.ToolResult> {
  abstract override suspend fun handle(input: I, meta: RequestMeta?): ToolResult

  final override suspend fun handle(input: I): ToolResult = handle(input, null)
}
