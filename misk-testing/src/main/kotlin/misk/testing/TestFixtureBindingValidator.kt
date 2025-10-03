package misk.testing

import com.google.inject.Binding
import com.google.inject.Injector
import com.google.inject.Key
import com.google.inject.TypeLiteral
import com.google.inject.spi.LinkedKeyBinding
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import misk.logging.getLogger
import kotlin.collections.iterator

/**
 * Validates that all classes implementing or extending TestFixture are properly bound in Guice modules with both the
 * interface binding and the TestFixture multibinding.
 *
 * This helps catch a common mistake where engineers forget to add the
 * `multibind<TestFixture>().to<FakeImplementation>()` line, which can cause flaky tests that are hard to debug.
 *
 * Usage in tests:
 * ```
 * @MiskTest(startService = false)
 * class MyServiceTestingModuleValidationTest {
 *   @Inject lateinit var injector: Injector
 *
 *   @Test
 *   fun `TestingModule has proper TestFixture bindings`() {
 *     val validator = TestFixtureBindingValidator(injector)
 *     val errors = validator.validate()
 *     assertThat(errors).isEmpty()
 *   }
 * }
 * ```
 */
class TestFixtureBindingValidator(private val injector: Injector) {

  companion object {
    private val logger = getLogger<TestFixtureBindingValidator>()
  }

  /**
   * Validates all TestFixture implementations have proper bindings. Returns a list of validation errors, empty if all
   * bindings are correct.
   */
  fun validate(): List<String> {
    return try {
      val allBindings = injector.allBindings
      val testFixtureInterfaceBindings = mutableMapOf<Class<*>, MutableSet<Class<*>>>()
      val testFixtureMultibindings = mutableSetOf<Class<*>>()

      analyzeBindings(allBindings, testFixtureInterfaceBindings, testFixtureMultibindings)
      generateValidationErrors(testFixtureInterfaceBindings, testFixtureMultibindings)
    } catch (e: Exception) {
      logger.error("Error during TestFixture validation", e)
      emptyList()
    }
  }

  private fun analyzeBindings(
    allBindings: Map<Key<*>, Binding<*>>,
    testFixtureInterfaceBindings: MutableMap<Class<*>, MutableSet<Class<*>>>,
    testFixtureMultibindings: MutableSet<Class<*>>,
  ) {
    for ((key, binding) in allBindings) {
      try {
        val typeLiteral = key.typeLiteral

        if (isTestFixtureMultibinding(typeLiteral)) {
          processTestFixtureMultibinding(key, binding, testFixtureMultibindings)
        } else {
          processInterfaceBinding(binding, testFixtureInterfaceBindings)
        }
      } catch (e: Exception) {
        logger.debug("Skipping binding analysis for $key (type: ${key.typeLiteral.rawType.simpleName}): ${e.message}")
      }
    }
  }

  private fun processTestFixtureMultibinding(
    key: Key<*>,
    binding: Binding<*>,
    testFixtureMultibindings: MutableSet<Class<*>>,
  ) {
    try {
      val provider = binding.provider
      val instance = provider.get()
      if (instance is Set<*>) {
        instance.filterIsInstance<TestFixture>().forEach { fixture -> testFixtureMultibindings.add(fixture.javaClass) }
      }
    } catch (e: Exception) {
      logger.debug("Could not evaluate TestFixture multibinding for ${key}: ${e.message}")
    }
  }

  private fun processInterfaceBinding(
    binding: Binding<*>,
    testFixtureInterfaceBindings: MutableMap<Class<*>, MutableSet<Class<*>>>,
  ) {
    val implementationClass = getImplementationClass(binding)
    if (implementationClass != null && isTestFixtureImplementation(implementationClass)) {
      val keyClass = binding.key.typeLiteral.rawType
      if (keyClass.isInterface && keyClass != TestFixture::class.java) {
        testFixtureInterfaceBindings.getOrPut(implementationClass) { mutableSetOf() }.add(keyClass)
      }
    }
  }

  private fun generateValidationErrors(
    testFixtureInterfaceBindings: Map<Class<*>, Set<Class<*>>>,
    testFixtureMultibindings: Set<Class<*>>,
  ): List<String> {
    val errors = mutableListOf<String>()

    // Validate: interface bindings without multibindings
    for ((implementationClass, interfaces) in testFixtureInterfaceBindings) {
      if (!testFixtureMultibindings.contains(implementationClass)) {
        val interfaceNames = interfaces.map { it.simpleName }.sorted()
        errors.add(
          "Class ${implementationClass.simpleName} is bound to interface(s) [${interfaceNames.joinToString(", ")}] " +
            "but missing multibind<TestFixture>().to<${implementationClass.simpleName}>()"
        )
      }
    }

    // Log debug info for multibindings without interface bindings (might be intentional)
    for (multibindingClass in testFixtureMultibindings) {
      if (!testFixtureInterfaceBindings.containsKey(multibindingClass)) {
        logger.debug("TestFixture ${multibindingClass.simpleName} has multibinding but no interface binding")
      }
    }

    return errors
  }

  /** Logs validation results. Call this during application startup in test environments. */
  fun validateAndLog() {
    val errors = validate()
    if (errors.isNotEmpty()) {
      logger.error("TestFixture binding validation failed:")
      errors.forEach { error -> logger.error("  - $error") }
      logger.error(
        """
        |
        |To fix these issues, ensure that for each Fake class that extends FakeFixture,
        |your Guice module includes both bindings:
        |  bind<YourInterface>().to<YourFakeImplementation>()
        |  multibind<TestFixture>().to<YourFakeImplementation>()
        |
        |The multibind line is required for proper test fixture reset between tests.
        |
        |Example:
        |class FakeServiceModule : KAbstractModule() {
        |  override fun configure() {
        |    bind<ServiceInterface>().to<FakeService>()
        |    multibind<TestFixture>().to<FakeService>()  // <- Don't forget this line!
        |  }
        |}
      """
          .trimMargin()
      )
    } else {
      logger.info("All TestFixture bindings are properly configured")
    }
  }

  /**
   * Gets the implementation class from a binding without instantiating it. Returns null if the implementation class
   * cannot be determined safely.
   */
  private fun getImplementationClass(binding: Binding<*>): Class<*>? {
    return try {
      when (binding) {
        is LinkedKeyBinding<*> -> {
          // For linked bindings like bind<Interface>().to<Implementation>()
          // we can get the implementation class from the linked key
          binding.linkedKey.typeLiteral.rawType
        }
        else -> {
          // For other binding types, we might need to instantiate to know the type
          // As a fallback, try to get it from the key if it's a concrete class
          val keyType = binding.key.typeLiteral.rawType
          if (!keyType.isInterface && !Modifier.isAbstract(keyType.modifiers)) {
            keyType
          } else {
            null
          }
        }
      }
    } catch (e: Exception) {
      logger.debug("Could not determine implementation class for binding ${binding.key}: ${e.message}")
      null
    }
  }

  /** Checks if a class implements TestFixture without instantiating it. */
  private fun isTestFixtureImplementation(clazz: Class<*>): Boolean {
    return TestFixture::class.java.isAssignableFrom(clazz)
  }

  private fun isTestFixtureMultibinding(typeLiteral: TypeLiteral<*>): Boolean {
    val type = typeLiteral.type
    if (type is ParameterizedType) {
      val rawType = type.rawType
      val typeArgs = type.actualTypeArguments
      return rawType == Set::class.java && typeArgs.size == 1 && typeArgs[0] == TestFixture::class.java
    }
    return false
  }
}
