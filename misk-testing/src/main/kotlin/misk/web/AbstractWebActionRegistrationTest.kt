package misk.web

import com.google.inject.Injector
import misk.web.actions.WebAction
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

/**
 * Abstract base class for testing that all WebAction implementations are properly registered.
 *
 * Extend this class and implement the abstract methods to get automatic verification that
 * all WebAction implementations in your service's packages are registered via
 * `WebActionModule.create<YourAction>()`.
 *
 * ## Usage
 *
 * ```kotlin
 * class WebActionRegistrationTest : AbstractWebActionRegistrationTest() {
 *   override fun createInjector(): Injector {
 *     // Use your service's testing module or injector builder
 *     return Guice.createInjector(MyServiceTestingModule())
 *   }
 *
 *   override fun webActionPackages(): List<String> {
 *     return listOf("com.example.myservice")
 *   }
 *
 *   // Optional: exclude specific actions from verification
 *   override fun excludeWebAction(actionClass: KClass<out WebAction>): Boolean {
 *     return actionClass.simpleName == "SomeSpecialAction"
 *   }
 * }
 * ```
 *
 * ## What This Test Catches
 *
 * This test catches a common mistake: implementing a WebAction but forgetting to register it.
 * Without registration via `WebActionModule.create<YourAction>()`, the action won't be exposed
 * as an HTTP endpoint.
 *
 * ## Built-in Exclusions
 *
 * The following are automatically excluded from verification:
 * - Abstract classes and interfaces
 * - Classes in `.api.internal.` packages (typically Wire/gRPC-generated actions)
 *
 * Use [excludeWebAction] to add service-specific exclusions.
 */
abstract class AbstractWebActionRegistrationTest {

  /**
   * Creates the Guice injector for the service under test.
   *
   * This should return an injector configured with all the modules your service uses,
   * typically via your testing module or a similar mechanism.
   */
  protected abstract fun createInjector(): Injector

  /**
   * Returns the packages to scan for WebAction implementations.
   *
   * This should be your service's root package(s), e.g. `listOf("com.example.myservice")`.
   * Avoid overly broad packages like `listOf("com.example")` which might scan unrelated code.
   */
  protected abstract fun webActionPackages(): List<String>

  /**
   * Returns true if the given action class should be excluded from registration verification.
   *
   * Override this to exclude specific actions that are intentionally not registered,
   * such as test-only actions or actions registered via different mechanisms.
   *
   * Example:
   * ```kotlin
   * override fun excludeWebAction(actionClass: KClass<out WebAction>): Boolean {
   *   return actionClass.simpleName in setOf("TestOnlyAction", "SpecialAdminAction")
   * }
   * ```
   */
  protected open fun excludeWebAction(actionClass: KClass<out WebAction>): Boolean = false

  /**
   * Returns a hint for error messages about where to register missing actions.
   *
   * Override this to provide a clearer error message, e.g. `"WebModule"` or
   * `"config/modules/WebActionsModule"`.
   */
  protected open fun registrationModuleHint(): String? = null

  @Test
  fun allWebActionsAreRegistered() {
    val injector = createInjector()
    WebActionRegistrationTesting.assertAllWebActionsRegistered(
      injector,
      WebActionRegistrationTesting.Options(
        basePackages = webActionPackages(),
        excludePredicate = ::excludeWebAction,
        registrationModuleHint = registrationModuleHint(),
      ),
    )
  }
}
