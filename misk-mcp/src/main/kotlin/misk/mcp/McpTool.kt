package misk.mcp

import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import misk.annotation.ExperimentalMiskApi
import misk.mcp.internal.McpJson
import misk.mcp.internal.generateJsonSchema
import kotlin.reflect.KClass

/**
 * Abstraction for a tool in the Model Context Protocol (MCP) specification.
 *
 * This interface represents a tool that can be exposed through an MCP server, allowing
 * AI models and other clients to discover and invoke functionality. Tools are one of the
 * core primitives in MCP, enabling models to perform actions and retrieve information
 * from external systems.
 *
 * The MCP specification defines tools as discrete pieces of functionality that can be
 * called by clients with structured inputs and return structured outputs. Each tool
 * must have a unique name within its server context and provide a JSON schema
 * describing its expected input parameters.
 *
 * ## Implementation Requirements
 *
 * Implementations must:
 * - Provide a unique [name] within the server context
 * - Include a human-readable [description] explaining the tool's purpose
 * - Define an [inputSchema] that validates incoming parameters
 * - Implement the [handler] function to process requests and return results
 *
 * ## Registration
 *
 * Tools should be registered with an MCP server using [McpToolModule]:
 *
 * ```kotlin
 * // In your Guice module configuration
 * install(McpToolModule.create<MyCustomTool>("myServerName"))
 * ```
 *
 * ## Example Implementation
 *
 * ```kotlin
 * @Singleton
 * class CalculatorTool @Inject constructor() : McpTool {
 *   override val name = "calculator"
 *   override val description = "Performs basic arithmetic operations"
 *
 *   override val inputSchema = Tool.Input(
 *     type = "object",
 *     properties = mapOf(
 *       "operation" to mapOf("type" to "string", "enum" to listOf("add", "subtract")),
 *       "a" to mapOf("type" to "number"),
 *       "b" to mapOf("type" to "number")
 *     ),
 *     required = listOf("operation", "a", "b")
 *   )
 *
 *   override suspend fun handler(request: CallToolRequest): CallToolResult {
 *     val args = request.params.arguments
 *     val operation = args["operation"] as String
 *     val a = (args["a"] as Number).toDouble()
 *     val b = (args["b"] as Number).toDouble()
 *
 *     val result = when (operation) {
 *       "add" -> a + b
 *       "subtract" -> a - b
 *       else -> throw IllegalArgumentException("Unknown operation: $operation")
 *     }
 *
 *     return CallToolResult(
 *       content = listOf(
 *         TextContent(text = "Result: $result")
 *       )
 *     )
 *   }
 * }
 * ```
 *
 * @see <a href="https://modelcontextprotocol.io/specification/2025-06-18">MCP Specification</a>
 * @see McpToolModule for registration and dependency injection
 */
@ExperimentalMiskApi
abstract class McpTool<R : Any>(
  val inputClass: KClass<R>
) {
  /**
   * The unique identifier for this tool within the MCP server context.
   *
   * Must be unique among all tools registered with the same server instance.
   * Should use lowercase letters, numbers, and underscores for consistency.
   */
  abstract val name: String

  /**
   * Human-readable description of what this tool does.
   *
   * This description is exposed to clients and AI models to help them understand
   * when and how to use this tool. Should be clear and concise, explaining the
   * tool's purpose and expected behavior.
   */
  abstract val description: String

  val inputSchema: Tool.Input by lazy {
    val schema = inputClass.generateJsonSchema()
    Tool.Input(
      properties = requireNotNull(schema["properties"] as? JsonObject) {
        "Input schema must have properties defined"
      },
      required = requireNotNull(schema["required"] as? JsonArray) {
        "Input schema must have required properties defined"
      }.map { it.jsonPrimitive.content },
    )
  }

  /**
   * Handles incoming tool invocation requests.
   *
   * This suspend function processes the tool call request, validates inputs,
   * performs the requested operation, and returns structured results. The function
   * should handle errors gracefully and return appropriate error responses when
   * operations fail.
   *
   * @param request The incoming tool call request containing parameters and metadata
   * @return The result of the tool execution, including any content or error information
   * @throws Exception if the tool execution fails catastrophically
   */
  abstract suspend fun handle(request: CallToolRequest): CallToolResult


  protected inline fun <reified R> CallToolRequest.parseInput(): R = McpJson.decodeFromJsonElement(arguments)
}


