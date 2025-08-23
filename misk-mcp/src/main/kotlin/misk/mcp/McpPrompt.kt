package misk.mcp

import io.modelcontextprotocol.kotlin.sdk.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.PromptArgument
import misk.annotation.ExperimentalMiskApi

/**
 * Abstraction for a prompt in the Model Context Protocol (MCP) specification.
 *
 * This interface represents a prompt template that can be exposed through an MCP server,
 * allowing AI models and other clients to discover and retrieve structured prompt content.
 * Prompts are one of the core primitives in MCP, enabling models to access pre-defined
 * prompt templates with dynamic arguments for consistent and reusable interactions.
 *
 * The MCP specification defines prompts as reusable templates that can be parameterized
 * with arguments to generate contextual content for AI models. Each prompt must have a
 * unique name within its server context and can define optional arguments that customize
 * the prompt's behavior or content.
 *
 * ## Implementation Requirements
 *
 * Implementations must:
 * - Provide a unique [name] within the server context
 * - Include a human-readable [description] explaining the prompt's purpose
 * - Define [arguments] that specify what parameters the prompt accepts
 * - Implement the [handler] function to generate prompt content based on arguments
 *
 * ## Registration
 *
 * Prompts should be registered with an MCP server using [McpPromptModule]:
 *
 * ```kotlin
 * // In your Guice module configuration
 * install(McpPromptModule.create<MyCustomPrompt>("myServerName"))
 * ```
 *
 * ## Example Implementation
 *
 * ```kotlin
 * @Singleton
 * class CodeReviewPrompt @Inject constructor() : McpPrompt {
 *   override val name = "code_review"
 *   override val description = "Generate a code review prompt for the given programming language and code snippet"
 *
 *   override val arguments = listOf(
 *     PromptArgument(
 *       name = "language",
 *       description = "The programming language of the code",
 *       required = true
 *     ),
 *     PromptArgument(
 *       name = "code",
 *       description = "The code snippet to review",
 *       required = true
 *     ),
 *     PromptArgument(
 *       name = "focus",
 *       description = "Specific aspects to focus on (security, performance, style)",
 *       required = false
 *     )
 *   )
 *
 *   override suspend fun handler(request: GetPromptRequest): GetPromptResult {
 *     val args = request.params.arguments ?: emptyMap()
 *     val language = args["language"] ?: "unknown"
 *     val code = args["code"] ?: ""
 *     val focus = args["focus"] ?: "general best practices"
 *
 *     val promptText = """
 *       Please review the following $language code and provide feedback focusing on $focus:
 *
 *       ```$language
 *       $code
 *       ```
 *
 *       Please provide:
 *       1. Overall assessment
 *       2. Specific issues or improvements
 *       3. Best practice recommendations
 *     """.trimIndent()
 *
 *     return GetPromptResult(
 *       description = "Code review prompt for $language code",
 *       messages = listOf(
 *         PromptMessage(
 *           role = MessageRole.USER,
 *           content = TextContent(text = promptText)
 *         )
 *       )
 *     )
 *   }
 * }
 * ```
 *
 * @see <a href="https://modelcontextprotocol.io/specification/2025-06-18">MCP Specification</a>
 * @see McpPromptModule for registration and dependency injection
 */
@ExperimentalMiskApi
interface McpPrompt {
  /**
   * The unique identifier for this prompt within the MCP server context.
   *
   * Must be unique among all prompts registered with the same server instance.
   * Should use lowercase letters, numbers, and underscores for consistency.
   */
  val name: String

  /**
   * Human-readable description of what this prompt does.
   *
   * This description is exposed to clients and AI models to help them understand
   * the purpose and expected use case of this prompt template. Should be clear
   * and concise, explaining what kind of content the prompt generates.
   */
  val description: String

  /**
   * List of arguments that this prompt accepts for customization.
   *
   * These arguments define the parameters that can be passed to the prompt
   * to customize its behavior or content. Each argument specifies its name,
   * description, and whether it's required. Arguments allow prompts to be
   * flexible and reusable across different contexts.
   */
  val arguments: List<PromptArgument>

  /**
   * Handles incoming prompt generation requests.
   *
   * This suspend function processes the prompt request, extracts any provided
   * arguments, generates the appropriate prompt content, and returns structured
   * results. The function should handle missing or invalid arguments gracefully
   * and provide meaningful default behavior when possible.
   *
   * @param request The incoming prompt request containing arguments and metadata
   * @return The generated prompt result with messages and content
   * @throws Exception if the prompt generation fails catastrophically
   */
  suspend fun handler(request: GetPromptRequest): GetPromptResult
}
