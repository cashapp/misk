package misk.mcp

import io.modelcontextprotocol.kotlin.sdk.types.RequestMeta
import misk.annotation.ExperimentalMiskApi

/**
 * Capability interface declaring the meta-aware overload of [handle].
 *
 * This interface is the framework's hook for tools that need access to the inbound MCP request's `_meta` field
 * ([RequestMeta]). The dispatcher branches on `tool is MetaAwareTool<*, *>` at invocation time, so non-meta-aware tools
 * pay zero cost.
 *
 * Tool authors should not implement this interface directly — instead, extend the appropriate abstract bridge class for
 * their base hierarchy:
 * - [MetaAwareMcpTool] — for plain `McpTool` authors
 * - [MetaAwareStructuredMcpTool] — for `StructuredMcpTool` authors
 *
 * The bridges hold a `final override` of the input-only `handle(input)` method that delegates to the meta-aware
 * overload with `meta = null`, so meta-aware authors only override the meta-aware overload — single inheritance, single
 * method.
 *
 * External base hierarchies (e.g. cash-server's `MoneybotTool`) can add their own bridge by implementing this interface
 * and providing the same `final override` of the base's input-only method.
 *
 * Parameterized in [R] so it composes with bases that return covariant subtypes of `ToolResult` or external result
 * types entirely.
 *
 * @param I The tool's input type.
 * @param R The tool's return type.
 */
@ExperimentalMiskApi
interface MetaAwareTool<I, R> {
  /** Handles a tool invocation with the typed [input] and the request's optional [meta]. */
  suspend fun handle(input: I, meta: RequestMeta?): R
}
