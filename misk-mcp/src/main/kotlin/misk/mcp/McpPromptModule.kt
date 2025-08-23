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
 * @param T The type of [McpPrompt] implementation to register
 * @param promptClass The [KClass] of the prompt implementation
 *
 * @see McpPrompt for prompt implementation details
 * @see McpServerModule for server configuration
 * @see <a href="https://modelcontextprotocol.io">MCP Specification</a>
 */
@ExperimentalMiskApi
class McpPromptModule<T : McpPrompt>(
  private val promptClass: KClass<T>,
) : KAbstractModule() {

  override fun configure() {
    // Bind the MCP prompt to the named server's prompt set
    multibind<McpPrompt>()
      .to(promptClass.java)
      .asSingleton()
  }

  companion object {

    /**
     * Creates an [McpPromptModule] for the specified prompt class.
     *
     * @param T The type of [McpPrompt] implementation to register
     * @param promptClass The [KClass] of the prompt implementation
     * @return A configured [McpPromptModule] instance
     */
    fun <T : McpPrompt> create(promptClass: KClass<T>) =
      McpPromptModule(
        promptClass = promptClass,
      )

    /**
     * Creates an [McpPromptModule] using reified generics for convenient registration.
     *
     * This inline function allows you to specify the prompt type without explicitly
     * passing the [KClass], making prompt registration more concise and type-safe.
     *
     * @param T The type of [McpPrompt] implementation to register (inferred)
     * @return A configured [McpPromptModule] instance
     */
    inline fun <reified T : McpPrompt> create() =
      create(
        promptClass = T::class,
      )
  }
}
