package misk.mcp

import misk.annotation.ExperimentalMiskApi
import misk.inject.BindingQualifier
import misk.inject.KAbstractModule
import misk.inject.qualifier
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
 *     // Register prompts without grouping (default)
 *     install(McpPromptModule.create<CodeReviewPrompt>())
 *     install(McpPromptModule.create<DocumentationPrompt>())
 *     install(McpPromptModule.create<RefactoringPrompt>())
 *   }
 * }
 * ```
 *
 * ## Prompt Grouping with BindingQualifiers
 *
 * Prompts can be organized into groups using [BindingQualifier] annotations. This allows multiple
 * MCP servers to expose different sets of prompts. Define a qualifier annotation and use it when
 * creating both the server and prompt modules:
 *
 * ```kotlin
 * @Qualifier
 * @Retention(AnnotationRetention.RUNTIME)
 * annotation class AdminMcp
 *
 * @Qualifier
 * @Retention(AnnotationRetention.RUNTIME)
 * annotation class PublicMcp
 *
 * class MyApplicationModule : KAbstractModule() {
 *   override fun configure() {
 *     // Create servers with different qualifiers
 *     install(McpServerModule.create<AdminMcp>("admin_server", config.mcp))
 *     install(McpServerModule.create<PublicMcp>("public_server", config.mcp))
 *
 *     // Register prompts to specific servers using qualifiers
 *     install(McpPromptModule.create<AdminMcp, AdminPrompt>())
 *     install(McpPromptModule.create<PublicMcp, PublicPrompt>())
 *   }
 * }
 * ```
 *
 * You can also use annotation instances for dynamic grouping:
 *
 * ```kotlin
 * val adminAnnotation = AdminMcp()
 * install(McpPromptModule.create<MyPrompt>(adminAnnotation))
 * ```
 *
 * ## Server Configuration
 *
 * Prompts are made available through the MCP server configured via [McpServerModule].
 * All registered prompts will be exposed through the server's HTTP endpoints.
 *
 * @param P The type of [McpPrompt] implementation to register
 * @param promptClass The [KClass] of the prompt implementation
 * @param qualifier The [BindingQualifier] used to group this prompt with a specific MCP server
 *
 * @see McpPrompt for prompt implementation details
 * @see McpServerModule for server configuration
 * @see BindingQualifier for grouping prompts with servers
 * @see <a href="https://modelcontextprotocol.io">MCP Specification</a>
 */
@ExperimentalMiskApi
class McpPromptModule<P : McpPrompt> private constructor(
  private val promptClass: KClass<P>,
  private val qualifier: BindingQualifier?,
) : KAbstractModule() {

  override fun configure() {
    // Bind the MCP prompt to the named server's prompt set
    multibind<McpPrompt>(qualifier)
      .to(promptClass.java)
  }

  companion object {
    /**
     * Creates an [McpPromptModule] with an optional group annotation class.
     *
     * This is the base factory method that accepts a [KClass] for both the prompt
     * and the group annotation. Use the reified generic versions for more convenient
     * type-safe creation.
     *
     * @param P The type of [McpPrompt] implementation to register
     * @param promptClass The [KClass] of the prompt implementation
     * @param groupAnnotationClass Optional annotation class for grouping this prompt with a specific MCP server
     * @return A configured McpPromptModule instance
     */
    fun <P: McpPrompt> create(promptClass: KClass<P>, groupAnnotationClass: KClass<out Annotation>?) =
      McpPromptModule(
        promptClass = promptClass,
        qualifier = groupAnnotationClass?.qualifier,
      )

    /**
     * Creates an [McpPromptModule] with reified type parameters for both group annotation and prompt.
     *
     * This is the recommended way to register prompts with a specific MCP server group.
     * Both the group annotation and prompt type are specified using reified generics for
     * compile-time type safety.
     *
     * Example:
     * ```kotlin
     * install(McpPromptModule.create<AdminMcp, CodeReviewPrompt>())
     * ```
     *
     * @param GA The annotation type for the prompt's MCP group (e.g., @AdminMcp, @PaymentsMcp)
     * @param P The type of [McpPrompt] implementation to register
     * @return A configured McpPromptModule instance
     */
    inline fun <reified GA : Annotation, reified P : McpPrompt> create() =
      create(
        promptClass = P::class,
        groupAnnotationClass = GA::class,
      )

    /**
     * Creates an [McpPromptModule] without any group annotation.
     *
     * Use this when registering a prompt with the default (ungrouped) MCP server.
     * The prompt will be available to any MCP server that doesn't specify a group annotation.
     *
     * Example:
     * ```kotlin
     * install(McpPromptModule.create<DocumentationPrompt>())
     * ```
     *
     * @param T The type of [McpPrompt] implementation to register
     * @return A configured McpPromptModule instance with no group annotation
     */
    @JvmName("createWithNoGroup")
    inline fun <reified T : McpPrompt> create() =
      create(
        promptClass = T::class,
        groupAnnotationClass = null,
      )

    /**
     * Creates an [McpPromptModule] with an annotation instance for dynamic grouping.
     *
     * Use this when you need to create group annotations dynamically at runtime
     * rather than using compile-time annotation classes.
     *
     * @param R The type of [McpPrompt] implementation to register
     * @param promptClass The [KClass] of the prompt implementation
     * @param groupAnnotation Optional annotation instance for grouping this prompt with a specific MCP server
     * @return A configured McpPromptModule instance
     */
    fun <R : McpPrompt> create(promptClass: KClass<R>, groupAnnotation: Annotation?) =
      McpPromptModule(
        promptClass = promptClass,
        qualifier = groupAnnotation?.qualifier,
      )

    /**
     * Creates an [McpPromptModule] with a reified prompt type and annotation instance.
     *
     * Convenience method that combines reified prompt type with runtime annotation instance.
     *
     * @param P The type of [McpPrompt] implementation to register
     * @param groupAnnotation Optional annotation instance for grouping this prompt with a specific MCP server
     * @return A configured McpPromptModule instance
     */
    inline fun <reified P : McpPrompt> create(groupAnnotation: Annotation?) =
      create(
        promptClass = P::class,
        groupAnnotation = groupAnnotation,
      )
  }
}
