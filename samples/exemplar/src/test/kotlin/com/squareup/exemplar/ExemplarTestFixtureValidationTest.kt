package com.squareup.exemplar

import com.google.inject.Injector
import com.google.inject.Module
import jakarta.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.TestFixtureBindingValidator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest
class ExemplarTestFixtureValidationTest {
  /**
   * Validates that all TestFixture implementations in ExemplarTestModule have proper Guice bindings.
   *
   * This test ensures that all fake clients and services used in Exemplar tests have both:
   * 1. Interface binding: bind<Interface>().to<FakeImplementation>()
   * 2. TestFixture multibinding: multibind<TestFixture>().to<FakeImplementation>()
   *
   * If this test fails, it means some fake implementations are missing the TestFixture multibinding, which can cause
   * flaky tests due to improper fixture reset between tests.
   */
  @MiskTestModule val module: Module = ExemplarTestModule()

  @Inject private lateinit var injector: Injector

  @Test
  fun `ExemplarTestModule has proper TestFixture bindings`() {
    val validator = TestFixtureBindingValidator(injector)
    val errors = validator.validate()

    if (errors.isNotEmpty()) {
      // Log detailed error information for debugging
      validator.validateAndLog()

      // Fail the test with a clear message
      val errorMessage = buildString {
        appendLine("ExemplarTestModule has ${errors.size} TestFixture binding issue(s):")
        errors.forEach { error -> appendLine("  - $error") }
        appendLine()
        appendLine("Fix by adding the missing multibind<TestFixture>() lines to the appropriate modules.")
        appendLine("See the logged error messages above for specific instructions.")
      }

      assertThat(errors).withFailMessage(errorMessage).isEmpty()
    }
  }

  @Test
  fun `validation runs without exceptions`() {
    // Ensure the validator itself doesn't crash on TestFixtureBindingValidator
    val validator = TestFixtureBindingValidator(injector)

    // This should not throw any exceptions
    val errors = validator.validate()

    // We don't assert on the content here, just that it completes successfully
    // The actual validation is done in the other test
    println("Validation completed successfully, found ${errors.size} issue(s)")
  }
}
