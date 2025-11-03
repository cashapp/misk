package misk.mcp

import misk.annotation.ExperimentalMiskApi
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import kotlin.reflect.KClass

/**
 * Module for registering [McpPrompt] implementations with an MCP server.
 *
 * This module registers MCP prompts that will be available through the configured MCP server.
 * Prompts provide reusable templates that AI models and other MCP clients can discover and use.
 *
 * ## Usage
 *
 * Register prompts using the reified generic create method:
 *
 * ```kotlin
 * class MyApplicationModule : KAbstractModule() {
 *   override fun configure() {
 *     // Register prompts
 *     install(McpPromptModule.create<CodeReviewPrompt>())
 *     install(McpPromptModule.create<DocumentationPrompt>())
 *     install(McpPromptModule.create<RefactoringPrompt>())
 *   }
 * }
 * ```
 *
 * ## Server Configuration
 *
 * Prompts are made available through the MCP server configured via [McpServerModule].
 * All registered prompts will be exposed through the server's HTTP endpoints.
 *
 * @param P The type of [McpPrompt] implementation to register
 * @param promptClass The [KClass] of the prompt implementation
 *
 * @see McpPrompt for prompt implementation details
 * @see McpServerModule for server configuration
 * @see <a href="https://modelcontextprotocol.io">MCP Specification</a>
 */
@ExperimentalMiskApi
class McpPromptModule<P : McpPrompt> private constructor(
  private val promptClass: KClass<P>,
  private val groupAnnotationClass: KClass<out Annotation>?,
) : KAbstractModule() {

  override fun configure() {
    // Bind the MCP prompt to the named server's prompt set
    multibind<McpPrompt>(groupAnnotationClass)
      .to(promptClass.java)
      .asSingleton()
  }

  companion object {
    fun <P: McpPrompt> create(promptClass: KClass<P>, groupAnnotationClass: KClass<out Annotation>?) =
      McpPromptModule(
        promptClass = promptClass,
        groupAnnotationClass = groupAnnotationClass,
      )

    /**
     * @param GA The annotation type for the tool's MCP group (e.g., @AdminMCP, @PaymentsMCP).
     * @param P The type of [McpPrompt] implementation to register
     */
    inline fun <reified GA : Annotation, reified P : McpPrompt> create() =
      create(
        promptClass = P::class,
        groupAnnotationClass = GA::class,
      )

    /**
     * @param T The type of [McpPrompt] implementation to register
     */
    @JvmName("createWithNoGroup")
    inline fun <reified T : McpPrompt> create() =
      create(
        promptClass = T::class,
        groupAnnotationClass = null,
      )
  }
}
