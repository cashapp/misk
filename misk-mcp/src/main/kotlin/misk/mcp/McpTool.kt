@file:Suppress("PropertyName", "LocalVariableName")

package misk.mcp

import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.PromptMessageContent
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import misk.annotation.ExperimentalMiskApi
import misk.mcp.internal.McpJson
import misk.mcp.internal.generateJsonSchema
import kotlin.reflect.KClass

/**
 * Base class for tools in the Model Context Protocol (MCP) specification with type-safe input handling.
 *
 * This abstract class represents a tool that can be exposed through an MCP server, allowing
 * AI models and other clients to discover and invoke functionality. Tools are one of the
 * core primitives in MCP, enabling models to perform actions and retrieve information
 * from external systems.
 *
 * The MCP specification defines tools as discrete pieces of functionality that can be
 * called by clients with structured inputs and return structured outputs. Each tool
 * must have a unique name within its server context and provide a JSON schema
 * describing its expected input parameters.
 *
 * ## Key Features
 *
 * - **Type-safe input handling**: Input is automatically deserialized to the specified type [I]
 * - **Automatic schema generation**: Input schema is generated from the Kotlin class definition
 * - **Type-safe result handling**: Results are wrapped in [ToolResult] sealed interface
 * - **Flexible output formats**: Support for prompt content or structured data (see [StructuredMcpTool])
 *
 * ## Implementation Requirements
 *
 * Implementations must:
 * - Provide a unique [name] within the server context
 * - Include a human-readable [description] explaining the tool's purpose
 * - Specify the input type parameter [I] which must be a serializable data class
 * - Implement the [handle] function to process typed inputs and return [ToolResult]
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
 * // Define input as a data class
 * @Serializable
 * data class CalculatorInput(
 *   val operation: String,
 *   val a: Double,
 *   val b: Double
 * )
 *
 * @Singleton
 * class CalculatorTool @Inject constructor() : McpTool<CalculatorInput>() {
 *   override val name = "calculator"
 *   override val description = "Performs basic arithmetic operations"
 *
 *   override suspend fun handle(input: CalculatorInput): ToolResult {
 *     val result = when (input.operation) {
 *       "add" -> input.a + input.b
 *       "subtract" -> input.a - input.b
 *       "multiply" -> input.a * input.b
 *       "divide" -> {
 *         if (input.b == 0.0) {
 *           return ToolResult(
 *             TextContent("Error: Division by zero"),
 *             isError = true
 *           )
 *         }
 *         input.a / input.b
 *       }
 *       else -> return ToolResult(
 *         TextContent("Unknown operation: ${input.operation}"),
 *         isError = true
 *       )
 *     }
 *
 *     return ToolResult(
 *       TextContent("Result: $result")
 *     )
 *   }
 * }
 * ```
 *
 * ## Working with ToolResult
 *
 * The [ToolResult] sealed interface provides type-safe result handling:
 *
 * ```kotlin
 * // Return a simple text result
 * return ToolResult(TextContent("Success!"))
 *
 * // Return multiple content items
 * return ToolResult(
 *   TextContent("Processing complete"),
 *   ImageContent(base64Data = imageData, mimeType = "image/png")
 * )
 *
 * // Return an error result
 * return ToolResult(
 *   TextContent("Failed to process request: $errorMessage"),
 *   isError = true
 * )
 *
 * // Include metadata
 * return ToolResult(
 *   TextContent("Result with metadata"),
 *   _meta = JsonObject(mapOf("processTime" to JsonPrimitive(123)))
 * )
 * ```
 *
 * @param I The input type for this tool, must be a serializable data class
 *
 * @see StructuredMcpTool for tools that return structured data
 * @see <a href="https://modelcontextprotocol.io/specification/2025-06-18">MCP Specification</a>
 * @see McpToolModule for registration and dependency injection
 */
@ExperimentalMiskApi
abstract class McpTool<I : Any> {
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

  /**
   * Human-readable title for this tool.
   *
   * Provides a user-friendly display name for the tool that may be shown in user interfaces.
   * Defaults to the tool's [name] if not overridden.
   */
  open val title: String = name

  /**
   * Hint indicating whether this tool performs read-only operations.
   *
   * When true, indicates that the tool only reads data and does not modify any state.
   * This can be used by clients to optimize caching or to provide appropriate UI indicators.
   * Defaults to false, indicating the tool may modify state.
   */
  open val readOnlyHint: Boolean = false

  /**
   * Hint indicating whether this tool performs destructive operations.
   *
   * When true, indicates that the tool may delete, overwrite, or otherwise destructively
   * modify data. Clients may use this hint to show warnings or require confirmation
   * before invoking the tool. Defaults to true for safety.
   *
   * Note: This hint is only relevant when [readOnlyHint] is false. Read-only tools
   * are inherently non-destructive.
   */
  open val destructiveHint: Boolean = true

  /**
   * Hint indicating whether this tool is idempotent.
   *
   * When true, indicates that calling the tool multiple times with the same input
   * will produce the same result and have the same effect as calling it once.
   * This can be useful for retry logic and error recovery. Defaults to false.
   *
   * Note: This hint is only relevant when [readOnlyHint] is false. Read-only tools
   * are inherently idempotent since they don't modify state.
   */
  open val idempotentHint: Boolean = false

  /**
   * Hint indicating whether this tool operates in an open-world context.
   *
   * When true, indicates that the tool may interact with external systems or resources
   * that are not fully controlled or predictable (e.g., network services, file systems).
   * When false, indicates the tool operates in a closed, predictable environment.
   * Defaults to true.
   */
  open val openWorldHint: Boolean = true

  internal val inputSchema: Tool.Input by lazy {
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

  internal open val outputSchema: Tool.Output? = null

  @OptIn(InternalSerializationApi::class)
  internal suspend fun handler(request: CallToolRequest): CallToolResult {
    // Parse the input arguments from the request
    val parsedInput = try {
      McpJson.decodeFromJsonElement(inputClass.serializer(), request.arguments)
    } catch (e: SerializationException) {
      return ToolResult(
        TextContent(
          "Failed to parse input for tool '$name'. " +
            "Expected input type: ${inputClass.simpleName}. " +
            "Error: ${e.message}",
        ),
        isError = true,
      ).toCallToolResult()
    } catch (e: IllegalArgumentException) {
      return ToolResult(
        TextContent(
          "Failed to parse input for tool '$name'. " +
            "Expected input type: ${inputClass.simpleName}. " +
            "Error: ${e.message}",
        ),
        isError = true,
      ).toCallToolResult()
    }
    return handle(parsedInput).toCallToolResult()
  }


  sealed interface ToolResult {
    val isError: Boolean
    val _meta: JsonObject
  }

  @ConsistentCopyVisibility
  data class PromptToolResult internal constructor(
    val result: List<PromptMessageContent>,
    override val isError: Boolean,
    override val _meta: JsonObject,
  ) : ToolResult


  protected fun ToolResult(
    vararg results: PromptMessageContent,
    isError: Boolean = false,
    _meta: JsonObject = JsonObject(emptyMap()),
  ): ToolResult = ToolResult(results.toList(), isError, _meta)

  protected fun ToolResult(
    result: List<PromptMessageContent>,
    isError: Boolean = false,
    _meta: JsonObject = JsonObject(emptyMap()),
  ): ToolResult = PromptToolResult(
    result = result,
    isError = isError,
    _meta = _meta,
  )

  protected open fun ToolResult.toCallToolResult() = when (this) {
    is PromptToolResult -> CallToolResult(
      content = result,
      isError = isError,
      _meta = _meta,
    )

    else -> {
      throw IllegalArgumentException("${this::class.simpleName} is not supported by this tool: $name")
    }
  }

  abstract suspend fun handle(input: I): ToolResult

  private val inputClass: KClass<I> by lazy {
    @Suppress("UNCHECKED_CAST")
    this::class.supertypes
      .first { type ->
        (type.classifier as? KClass<*>)?.simpleName?.let { simpleName ->
          simpleName == McpTool::class.simpleName
            || simpleName == StructuredMcpTool::class.simpleName
        } ?: false
      }
      .arguments.first().type!!.classifier as KClass<I>
  }
}

/**
 * Extended MCP tool class that supports structured output in addition to prompt content.
 *
 * This class extends [McpTool] to provide type-safe structured output handling, allowing tools
 * to return strongly-typed data structures that can be consumed by AI models and other clients.
 * The structured output is automatically serialized to JSON and included in the MCP response.
 *
 * ## Key Features
 *
 * - **Type-safe output**: Output is strongly typed using the [O] type parameter
 * - **Automatic output schema generation**: Output schema is generated from the Kotlin class definition
 * - **Dual output format**: Returns both text representation and structured JSON data
 * - **Full type safety**: Both input and output are type-checked at compile time
 *
 * ## When to Use StructuredMcpTool
 *
 * Use `StructuredMcpTool` when:
 * - Your tool returns complex data that AI models need to parse and understand
 * - You want to provide structured data that can be consumed programmatically
 * - The output has a well-defined schema that should be documented
 * - You need type safety for both input and output
 *
 * Use regular `McpTool` when:
 * - Your tool returns simple text or media content
 * - The output is primarily for human consumption
 * - The output structure is dynamic or varies significantly
 *
 * ## Example Implementation
 *
 * ```kotlin
 * // Define input and output as data classes
 * @Serializable
 * data class WeatherInput(
 *   val city: String,
 *   val units: String = "celsius"
 * )
 *
 * @Serializable
 * data class WeatherOutput(
 *   val city: String,
 *   val temperature: Double,
 *   val humidity: Int,
 *   val conditions: String,
 *   val windSpeed: Double,
 *   val units: String
 * )
 *
 * @Singleton
 * class WeatherTool @Inject constructor(
 *   private val weatherService: WeatherService
 * ) : StructuredMcpTool<WeatherInput, WeatherOutput>() {
 *   override val name = "get_weather"
 *   override val description = "Get current weather conditions for a city"
 *
 *   override suspend fun handle(input: WeatherInput): ToolResult {
 *     return try {
 *       val weatherData = weatherService.getCurrentWeather(input.city, input.units)
 *
 *       // Return structured data - will be serialized to JSON automatically
 *       ToolResult(
 *         WeatherOutput(
 *           city = input.city,
 *           temperature = weatherData.temp,
 *           humidity = weatherData.humidity,
 *           conditions = weatherData.description,
 *           windSpeed = weatherData.windSpeed,
 *           units = input.units
 *         )
 *       )
 *     } catch (e: CityNotFoundException) {
 *       // Can still return prompt content for errors
 *       ToolResult(
 *         TextContent("City '${input.city}' not found"),
 *         isError = true
 *       )
 *     }
 *   }
 * }
 * ```
 *
 * ## Complex Example with Mixed Output
 *
 * ```kotlin
 * @Serializable
 * data class AnalysisInput(
 *   val datasetId: String,
 *   val metrics: List<String>
 * )
 *
 * @Serializable
 * data class AnalysisOutput(
 *   val datasetId: String,
 *   val results: Map<String, Double>,
 *   val summary: Summary,
 *   val generatedAt: String
 * ) {
 *   @Serializable
 *   data class Summary(
 *     val totalRecords: Int,
 *     val processingTime: Long,
 *     val warnings: List<String> = emptyList()
 *   )
 * }
 *
 * @Singleton
 * class DataAnalysisTool @Inject constructor(
 *   private val analyzer: DataAnalyzer
 * ) : StructuredMcpTool<AnalysisInput, AnalysisOutput>() {
 *   override val name = "analyze_dataset"
 *   override val description = "Perform statistical analysis on a dataset"
 *
 *   override suspend fun handle(input: AnalysisInput): ToolResult {
 *     val startTime = System.currentTimeMillis()
 *
 *     val dataset = analyzer.loadDataset(input.datasetId)
 *     val results = mutableMapOf<String, Double>()
 *     val warnings = mutableListOf<String>()
 *
 *     for (metric in input.metrics) {
 *       try {
 *         results[metric] = analyzer.calculate(dataset, metric)
 *       } catch (e: UnsupportedMetricException) {
 *         warnings.add("Metric '$metric' is not supported")
 *       }
 *     }
 *
 *     if (results.isEmpty()) {
 *       return ToolResult(
 *         TextContent("No valid metrics could be calculated"),
 *         isError = true
 *       )
 *     }
 *
 *     return ToolResult(
 *       AnalysisOutput(
 *         datasetId = input.datasetId,
 *         results = results,
 *         summary = AnalysisOutput.Summary(
 *           totalRecords = dataset.size,
 *           processingTime = System.currentTimeMillis() - startTime,
 *           warnings = warnings
 *         ),
 *         generatedAt = Instant.now().toString()
 *       ),
 *       _meta = JsonObject(mapOf(
 *         "version" to JsonPrimitive("1.0"),
 *         "analyzer" to JsonPrimitive("statistical-v2")
 *       ))
 *     )
 *   }
 * }
 * ```
 *
 * ## Output Handling
 *
 * When a `StructuredMcpTool` returns a [StructuredToolResult]:
 * - The result object is serialized to JSON using kotlinx.serialization
 * - The JSON is included as both text content and structured content in the response
 * - The output schema is automatically exposed to clients for validation
 * - AI models can parse and understand the structured data format
 *
 * The tool can still return [PromptToolResult] for error cases or when structured
 * output is not appropriate:
 *
 * ```kotlin
 * override suspend fun handle(input: I): ToolResult {
 *   return if (someErrorCondition) {
 *     // Return prompt content for errors
 *     ToolResult(
 *       TextContent("Error message"),
 *       isError = true
 *     )
 *   } else {
 *     // Return structured data for success
 *     ToolResult(outputData)
 *   }
 * }
 * ```
 *
 * @param I The input type for this tool, must be a serializable data class
 * @param O The output type for this tool, must be a serializable data class
 *
 * @see McpTool for the base tool implementation
 * @see <a href="https://modelcontextprotocol.io/specification/2025-06-18">MCP Specification</a>
 */
@ExperimentalMiskApi
abstract class StructuredMcpTool<I : Any, O : Any> : McpTool<I>() {


  override val outputSchema: Tool.Output by lazy {
    val schema = outputClass.generateJsonSchema()
    Tool.Output(
      properties = requireNotNull(schema["properties"] as? JsonObject) {
        "Output schema must have properties defined"
      },
      required = requireNotNull(schema["required"] as? JsonArray) {
        "Output schema must have required properties defined"
      }.map { it.jsonPrimitive.content },
    )
  }

  @OptIn(ExperimentalMiskApi::class)
  @ConsistentCopyVisibility
  data class StructuredToolResult<O : Any> internal constructor(
    val result: O,
    override val isError: Boolean,
    override val _meta: JsonObject,
  ) : ToolResult

  protected fun ToolResult(
    result: O,
    isError: Boolean = false,
    _meta: JsonObject = JsonObject(emptyMap()),
  ): ToolResult = StructuredToolResult(
    result = result,
    isError = isError,
    _meta = _meta,
  )

  @OptIn(InternalSerializationApi::class)
  override fun ToolResult.toCallToolResult(): CallToolResult {
    return when (this) {
      is PromptToolResult -> CallToolResult(
        content = result,
        isError = isError,
        _meta = _meta,
      )

      is StructuredToolResult<*> -> {
        @Suppress("UNCHECKED_CAST")
        val serializedOutput = McpJson.encodeToJsonElement(outputClass.serializer(), result as O) as JsonObject
        CallToolResult(
          // For backwards compatibility
          // See: [https://modelcontextprotocol.io/specification/2025-06-18/server/tools#structured-content]
          content = listOf(TextContent(serializedOutput.toString())),
          structuredContent = serializedOutput,
          isError = isError,
          _meta = _meta,
        )
      }
    }
  }

  private val outputClass: KClass<O> by lazy {
    @Suppress("UNCHECKED_CAST")
    this::class.supertypes
      .first { type ->
        (type.classifier as? KClass<*>)?.simpleName?.let { simpleName ->
          simpleName == StructuredMcpTool::class.simpleName
        } ?: false
      }
      .arguments.last().type!!.classifier as KClass<O>
  }
}
