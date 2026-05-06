package misk.mcp

import io.modelcontextprotocol.kotlin.sdk.types.RequestMeta
import misk.annotation.ExperimentalMiskApi

/**
 * Marker interface for MCP tools that need access to the request's `_meta` field.
 *
 * Mix this in alongside [McpTool] (or any subclass: [StructuredMcpTool], or external bases like
 * cash-server's `MoneybotTool`) to opt into receiving the inbound request's [RequestMeta].
 *
 * The dispatcher branches on `tool is MetaAwareMcpTool<*, *>` at invocation time, so non-meta-aware
 * tools pay zero cost and don't see this overload.
 *
 * The default implementation of [handle] (no-meta overload) delegates to [handle] (meta overload)
 * with `meta = null`. Because Kotlin requires explicit disambiguation when an abstract class
 * method shares a signature with an interface default, tools implementing both [McpTool] and
 * [MetaAwareMcpTool] must add a one-line forwarding override:
 *
 * ```kotlin
 * override suspend fun handle(input: I): ToolResult = super<MetaAwareMcpTool>.handle(input)
 * ```
 *
 * This forwards into the interface default (which calls `handle(input, null)`), so direct callers
 * of the no-meta overload — including test helpers — continue to work without re-implementing
 * the body.
 *
 * Parameterized in [R] so it composes with bases that return covariant subtypes of `ToolResult`
 * (e.g. [StructuredMcpTool] returning `StructuredToolResult<O>`, or external bases with their own
 * result types).
 *
 * @param I The tool's input type, matching the input type of the [McpTool] this is mixed into.
 * @param R The tool's return type, matching the return type of [McpTool.handle].
 */
@ExperimentalMiskApi
interface MetaAwareMcpTool<I, R> {
  /** Handles a tool invocation with the typed [input] and the request's optional [meta]. */
  suspend fun handle(input: I, meta: RequestMeta?): R

  /** Default no-meta overload — delegates to the meta-aware overload with `meta = null`. */
  suspend fun handle(input: I): R = handle(input, null)
}
