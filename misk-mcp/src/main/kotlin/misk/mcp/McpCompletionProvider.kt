package misk.mcp

import misk.annotation.ExperimentalMiskApi
import misk.mcp.protocol.CompleteRequest
import misk.mcp.protocol.PromptReference
import misk.mcp.protocol.Reference
import misk.mcp.protocol.ResourceTemplateReference

/**
 * Suggests values for the arguments of one prompt, or the variables of one resource template, through
 * `completion/complete`. Register it with [McpCompletionModule]; the server then advertises the `completions`
 * capability.
 *
 * ```kotlin
 * class LanguageCompletions @Inject constructor() : McpCompletionProvider {
 *   override val ref = PromptReference("code_review")
 *
 *   override suspend fun complete(request: CompleteRequest): List<String> =
 *     when (request.argument.name) {
 *       "language" -> listOf("java", "kotlin", "swift").filter { it.startsWith(request.argument.value) }
 *       else -> emptyList()
 *     }
 * }
 * ```
 */
@ExperimentalMiskApi
interface McpCompletionProvider {
  /**
   * What this provider completes: a [PromptReference] with a registered prompt's name, or a [ResourceTemplateReference]
   * with a registered template's URI template.
   */
  val ref: Reference

  /**
   * Returns the values that complete `request.argument`, best first. `request.context` holds the arguments the user
   * already chose. The client gets the first 100 values and the total count.
   */
  suspend fun complete(request: CompleteRequest): List<String>
}
