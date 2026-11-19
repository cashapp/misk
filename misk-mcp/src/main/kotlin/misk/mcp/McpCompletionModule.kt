package misk.mcp

import kotlin.reflect.KClass
import misk.annotation.ExperimentalMiskApi
import misk.inject.BindingQualifier
import misk.inject.KAbstractModule
import misk.inject.qualifier

/**
 * Registers an [McpCompletionProvider] with an MCP server.
 *
 * ```kotlin
 * install(McpCompletionModule.create<LanguageCompletions>())
 * ```
 */
@ExperimentalMiskApi
class McpCompletionModule<P : McpCompletionProvider>
private constructor(private val providerClass: KClass<P>, private val qualifier: BindingQualifier?) :
  KAbstractModule() {

  override fun configure() {
    // Bind the completion provider to the named server's provider set
    multibind<McpCompletionProvider>(qualifier).to(providerClass.java)
  }

  companion object {
    /**
     * Creates an [McpCompletionModule] with an optional group annotation class.
     *
     * This is the base factory method that accepts a [KClass] for both the completion provider and the group
     * annotation. Use the reified generic versions for more convenient type-safe creation.
     *
     * @param P The type of [McpCompletionProvider] implementation to register
     * @param providerClass The [KClass] of the completion provider implementation
     * @param groupAnnotationClass Optional annotation class for grouping this completion provider with a specific MCP
     *   server
     * @return A configured McpCompletionModule instance
     */
    fun <P : McpCompletionProvider> create(providerClass: KClass<P>, groupAnnotationClass: KClass<out Annotation>?) =
      McpCompletionModule(providerClass = providerClass, qualifier = groupAnnotationClass?.qualifier)

    /**
     * Creates an [McpCompletionModule] with reified type parameters for both group annotation and completion provider.
     *
     * This is the recommended way to register completion providers with a specific MCP server group. Both the group
     * annotation and completion provider type are specified using reified generics for compile-time type safety.
     *
     * Example:
     * ```kotlin
     * install(McpCompletionModule.create<AdminMcp, LanguageCompletions>())
     * ```
     *
     * @param GA The annotation type for the provider's MCP group (e.g., @AdminMcp, @PaymentsMcp)
     * @param P The type of [McpCompletionProvider] implementation to register
     * @return A configured McpCompletionModule instance
     */
    inline fun <reified GA : Annotation, reified P : McpCompletionProvider> create() =
      create(providerClass = P::class, groupAnnotationClass = GA::class)

    /**
     * Creates an [McpCompletionModule] without any group annotation.
     *
     * Use this when registering a completion provider with the default (ungrouped) MCP server. The completion provider
     * will be available to any MCP server that doesn't specify a group annotation.
     *
     * Example:
     * ```kotlin
     * install(McpCompletionModule.create<LanguageCompletions>())
     * ```
     *
     * @param T The type of [McpCompletionProvider] implementation to register
     * @return A configured McpCompletionModule instance with no group annotation
     */
    @JvmName("createWithNoGroup")
    inline fun <reified T : McpCompletionProvider> create() =
      create(providerClass = T::class, groupAnnotationClass = null)

    /**
     * Creates an [McpCompletionModule] with an annotation instance for dynamic grouping.
     *
     * Use this when you need to create group annotations dynamically at runtime rather than using compile-time
     * annotation classes.
     *
     * @param R The type of [McpCompletionProvider] implementation to register
     * @param providerClass The [KClass] of the completion provider implementation
     * @param groupAnnotation Optional annotation instance for grouping this completion provider with a specific MCP
     *   server
     * @return A configured McpCompletionModule instance
     */
    fun <R : McpCompletionProvider> create(providerClass: KClass<R>, groupAnnotation: Annotation?) =
      McpCompletionModule(providerClass = providerClass, qualifier = groupAnnotation?.qualifier)

    /**
     * Creates an [McpCompletionModule] with a reified completion provider type and annotation instance.
     *
     * Convenience method that combines reified completion provider type with runtime annotation instance.
     *
     * @param P The type of [McpCompletionProvider] implementation to register
     * @param groupAnnotation Optional annotation instance for grouping this completion provider with a specific MCP
     *   server
     * @return A configured McpCompletionModule instance
     */
    inline fun <reified P : McpCompletionProvider> create(groupAnnotation: Annotation?) =
      create(providerClass = P::class, groupAnnotation = groupAnnotation)
  }
}
