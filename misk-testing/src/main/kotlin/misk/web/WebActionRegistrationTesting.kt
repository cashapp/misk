package misk.web

import com.google.inject.Injector
import com.google.inject.Key
import com.google.inject.TypeLiteral
import io.github.classgraph.ClassGraph
import misk.web.actions.WebAction
import misk.web.actions.WebActionEntry
import org.assertj.core.api.Assertions.assertThat
import java.lang.reflect.Modifier
import kotlin.reflect.KClass

/**
 * Test utilities for verifying that all WebAction implementations are properly registered.
 *
 * This utility helps catch a common mistake: implementing a WebAction but forgetting to register
 * it via `WebActionModule.create<YourAction>()`. Without registration, the action won't be exposed
 * as an HTTP endpoint.
 *
 * ## Usage
 *
 * ```kotlin
 * @Test
 * fun allWebActionsAreRegistered() {
 *   val injector = // create your injector
 *   WebActionRegistrationTester.assertAllWebActionsRegistered(
 *     injector,
 *     WebActionRegistrationTester.Options(
 *       basePackages = listOf("com.example.myservice"),
 *     )
 *   )
 * }
 * ```
 *
 * Or use [AbstractWebActionRegistrationTest] for a simpler base class approach.
 */
object WebActionRegistrationTester {

  /**
   * Configuration options for web action registration testing.
   *
   * @param basePackages Packages to scan for WebAction implementations. Should be the service's
   *   root package(s), not broad packages like "com.squareup" which would scan too much.
   * @param excludePredicate Additional filter to exclude specific action classes beyond the
   *   built-in exclusions. Return true to exclude the action from verification.
   * @param registrationModuleHint A hint shown in error messages about where to register actions.
   *   Example: "WebModule" or "config/modules/WebActionsModule".
   */
  data class Options @JvmOverloads constructor(
    val basePackages: List<String>,
    val excludePredicate: (KClass<out WebAction>) -> Boolean = { false },
    val registrationModuleHint: String? = null,
  )

  /**
   * Built-in exclusions for classes that shouldn't be checked for registration.
   */
  private fun shouldExcludeByDefault(clazz: Class<*>): Boolean {
    return clazz.isInterface ||
      Modifier.isAbstract(clazz.modifiers) ||
      // Wire/gRPC-generated actions are registered via different mechanisms
      clazz.name.contains(".api.internal.")
  }

  /**
   * Asserts that all concrete WebAction implementations found in [options.basePackages] have a
   * corresponding [WebActionEntry] registered in the given [injector].
   *
   * This catches a common mistake: implementing a WebAction but forgetting to register it via
   * `WebActionModule.create<YourAction>()`.
   *
   * @param injector The Guice injector to check for registered actions.
   * @param options Configuration for scanning and exclusions.
   * @throws AssertionError if any WebAction implementations are found that aren't registered,
   *   with a helpful message including copy-paste registration code.
   */
  fun assertAllWebActionsRegistered(
    injector: Injector,
    options: Options,
  ) {
    val discoveredActions = discoverWebActions(options)
    val registeredActions = discoverRegisteredWebActions(injector)

    val missing = discoveredActions
      .filterNot { it in registeredActions }
      .sortedBy { it.qualifiedName }

    assertThat(missing)
      .overridingErrorMessage { buildMissingErrorMessage(missing, options.registrationModuleHint) }
      .isEmpty()
  }

  /**
   * Scans the classpath for concrete WebAction implementations in the specified packages.
   */
  private fun discoverWebActions(options: Options): Set<KClass<out WebAction>> {
    val packages = options.basePackages.toTypedArray()

    return ClassGraph()
      .enableClassInfo()
      .acceptPackages(*packages)
      .scan()
      .use { result ->
        result
          .getClassesImplementing(WebAction::class.java.name)
          .filter { classInfo ->
            val clazz = classInfo.loadClass()
            !shouldExcludeByDefault(clazz)
          }
          .map { it.loadClass().kotlin.asWebActionClass() }
          .filterNot { options.excludePredicate(it) }
          .toSet()
      }
  }

  /**
   * Discovers all WebAction classes that have been registered via WebActionEntry bindings.
   */
  private fun discoverRegisteredWebActions(injector: Injector): Set<KClass<out WebAction>> {
    // Try to get the multibound List<WebActionEntry> first
    return try {
      val entries = injector.getInstance(
        Key.get(object : TypeLiteral<List<WebActionEntry>>() {})
      )
      entries.map { it.actionClass }.toSet()
    } catch (e: Exception) {
      // Fall back to scanning all bindings if the list isn't bound
      discoverWebActionEntriesFromBindings(injector)
    }
  }

  /**
   * Fallback method to discover WebActionEntry bindings by scanning all injector bindings.
   * This handles cases where the multibound list isn't available.
   */
  private fun discoverWebActionEntriesFromBindings(injector: Injector): Set<KClass<out WebAction>> {
    val actionClasses = mutableSetOf<KClass<out WebAction>>()

    injector.allBindings.forEach { (key, binding) ->
      if (key.typeLiteral.rawType == WebActionEntry::class.java) {
        try {
          val entry = binding.provider.get() as WebActionEntry
          actionClasses.add(entry.actionClass)
        } catch (e: Exception) {
          // Skip bindings that can't be resolved
        }
      }
    }

    return actionClasses
  }

  /**
   * Builds a helpful error message for missing action registrations.
   */
  private fun buildMissingErrorMessage(
    missing: List<KClass<out WebAction>>,
    registrationModuleHint: String?,
  ): String {
    if (missing.isEmpty()) return ""

    val moduleHint = registrationModuleHint?.let { " in $it" } ?: ""

    return buildString {
      appendLine("The following WebActions are not registered:")
      missing.forEach { actionClass ->
        appendLine("  - ${actionClass.qualifiedName}")
      }
      appendLine()
      appendLine("Copy and paste the following lines into your WebAction registration module$moduleHint:")
      appendLine("-----")
      missing.forEach { actionClass ->
        appendLine("    install(WebActionModule.create<${actionClass.simpleName}>())")
        appendLine("or")
        appendLine("    install(WebActionModule.create<${actionClass.simpleName}>(\"<optional path prefix>\"))")
      }
      append("-----")
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun KClass<*>.asWebActionClass(): KClass<out WebAction> = this as KClass<out WebAction>
}
