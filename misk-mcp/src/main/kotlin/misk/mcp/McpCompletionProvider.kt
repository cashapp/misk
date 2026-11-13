package misk.mcp

import misk.annotation.ExperimentalMiskApi

/**
 * Opt-in interface for an [McpPrompt] or [McpResourceTemplate] that suggests argument values through the MCP
 * `completion/complete` request.
 *
 * Implement it next to [McpPrompt] or [McpResourceTemplate]. The server advertises the `completions` capability only
 * when at least one registered prompt or resource template implements this interface.
 *
 * ```kotlin
 * class CodeReviewPrompt @Inject constructor() : McpPrompt, McpCompletionProvider {
 *   override val name = "code_review"
 *   override val description = "Review code in a language"
 *   override val arguments = listOf(PromptArgument(name = "language", required = true))
 *
 *   override suspend fun complete(argumentName: String, value: String, contextArguments: Map<String, String>) =
 *     when (argumentName) {
 *       "language" -> listOf("java", "kotlin", "python").filter { it.startsWith(value) }
 *       else -> emptyList()
 *     }
 *
 *   override suspend fun handler(request: GetPromptRequest): GetPromptResult = TODO()
 * }
 * ```
 */
@ExperimentalMiskApi
interface McpCompletionProvider {
  /**
   * Returns suggested values for one argument, best match first.
   *
   * The server sends at most [MAX_COMPLETION_VALUES] values and reports the full count in `total` and `hasMore`.
   *
   * @param argumentName For a prompt, the name of one of its arguments. For a resource template, the name of one of its
   *   URI template variables.
   * @param value The partial value the user has entered so far.
   * @param contextArguments Values the client has already resolved for other arguments, keyed by argument name.
   */
  suspend fun complete(argumentName: String, value: String, contextArguments: Map<String, String>): List<String>

  companion object {
    /** The most completion values the MCP specification allows in one response. */
    const val MAX_COMPLETION_VALUES = 100
  }
}
