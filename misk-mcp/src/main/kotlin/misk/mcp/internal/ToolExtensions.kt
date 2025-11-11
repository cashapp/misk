@file:Suppress("PropertyName")

package misk.mcp.internal

import io.modelcontextprotocol.kotlin.sdk.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.Tool.Input
import io.modelcontextprotocol.kotlin.sdk.Tool.Output
import io.modelcontextprotocol.kotlin.sdk.ToolAnnotations
import kotlinx.serialization.json.JsonObject

internal class ToolBuilder(
  /**
   * The name of the tool.
   */
  var name: String,
  /**
   * A JSON object defining the expected parameters for the tool.
   */
  var inputSchema: Input,
) {

  /**
   * The title of the tool.
   */
  var title: String? = null

  /**
   * A human-readable description of the tool.
   */
  var description: String? = null

  /**
   * An optional JSON object defining the expected output schema for the tool.
   */
  var outputSchema: Output? = null

  /**
   * Optional additional tool information.
   */
  var annotations: ToolAnnotations? = null

  /**
   * Optional metadata for the tool.
   */
  var _meta: JsonObject = EmptyJsonObject

  fun build() = Tool(
    name = name,
    title = title,
    inputSchema = inputSchema,
    outputSchema = outputSchema,
    description = description,
    annotations = annotations,
    _meta = _meta,
  )
}

internal class ToolAnnotationBuilder {
  /**
   * A human-readable title for the tool.
   */
  var title: String? = null
  /**
   * If true, the tool does not modify its environment.
   *
   * Default: false
   */
  var readOnlyHint: Boolean? = false
  /**
   * If true, the tool may perform destructive updates to its environment.
   * If false, the tool performs only additive updates.
   *
   * (This property is meaningful only when `readOnlyHint == false`)
   *
   * Default: true
   */
  var destructiveHint: Boolean? = true
  /**
   * If true, calling the tool repeatedly with the same arguments
   * will have no additional effect on its environment.
   *
   * (This property is meaningful only when `readOnlyHint == false`)
   *
   * Default: false
   */
  var idempotentHint: Boolean? = false
  /**
   * If true, this tool may interact with an "open world" of external
   * entities. If false, the tool's domain of interaction is closed.
   * For example, the world of a web search tool is open, whereas that
   * of a memory tool is not.
   *
   * Default: true
   */
  var openWorldHint: Boolean? = true

  fun build() = ToolAnnotations(
    title = title,
    readOnlyHint = readOnlyHint,
    destructiveHint = destructiveHint,
    idempotentHint = idempotentHint,
    openWorldHint = openWorldHint,
  )
}

internal fun Tool.Companion.build(
  name: String,
  inputSchema: Input,
  builder: ToolBuilder.() -> Unit,
) = ToolBuilder(name, inputSchema).apply(builder).build()

internal fun ToolAnnotations.Companion.build(
  builder: ToolAnnotationBuilder.() -> Unit,
) = ToolAnnotationBuilder().apply(builder).build()