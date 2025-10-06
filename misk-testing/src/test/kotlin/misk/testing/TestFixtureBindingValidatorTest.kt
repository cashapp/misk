package misk.testing

import com.google.inject.Guice
import misk.inject.KAbstractModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TestFixtureBindingValidatorTest {

  // Test interfaces and implementations
  interface TestService

  interface AnotherService

  class FakeTestService : FakeFixture(), TestService {
    val data by resettable { mutableListOf<String>() }
  }

  class FakeAnotherService : FakeFixture(), AnotherService {
    val requests by resettable { mutableListOf<String>() }
  }

  // Module with correct bindings
  class CorrectModule : KAbstractModule() {
    override fun configure() {
      bind<TestService>().to<FakeTestService>()
      multibind<TestFixture>().to<FakeTestService>()

      bind<AnotherService>().to<FakeAnotherService>()
      multibind<TestFixture>().to<FakeAnotherService>()
    }
  }

  // Module missing TestFixture multibinding
  class MissingTestFixtureBindingModule : KAbstractModule() {
    override fun configure() {
      bind<TestService>().to<FakeTestService>()
      // Missing: multibind<TestFixture>().to<FakeTestService>()
    }
  }

  // Module missing interface binding
  class MissingInterfaceBindingModule : KAbstractModule() {
    override fun configure() {
      // Missing: bind<TestService>().to<FakeTestService>()
      multibind<TestFixture>().to<FakeTestService>()
    }
  }

  // Module with both bindings missing
  class MissingBothBindingsModule : KAbstractModule() {
    override fun configure() {
      // Both bindings missing - this might be intentional for some cases
    }
  }

  @Test
  fun `validates correct bindings successfully`() {
    val injector = Guice.createInjector(CorrectModule())
    val validator = TestFixtureBindingValidator(injector)

    val errors = validator.validate()

    assertThat(errors).isEmpty()
  }

  @Test
  fun `detects missing TestFixture multibinding`() {
    val injector = Guice.createInjector(MissingTestFixtureBindingModule())
    val validator = TestFixtureBindingValidator(injector)

    val errors = validator.validate()

    assertThat(errors).hasSize(1)
    assertThat(errors[0]).contains("FakeTestService")
    assertThat(errors[0]).contains("missing multibind<TestFixture>")
    assertThat(errors[0]).contains("TestService")
  }

  @Test
  fun `handles missing interface binding gracefully`() {
    val injector = Guice.createInjector(MissingInterfaceBindingModule())
    val validator = TestFixtureBindingValidator(injector)

    val errors = validator.validate()

    // This case is handled gracefully - TestFixture multibinding without interface binding
    // might be intentional, so we don't report it as an error
    assertThat(errors).isEmpty()
  }

  @Test
  fun `handles multiple validation errors`() {
    // Create a module that combines multiple issues
    class MultipleIssuesModule : KAbstractModule() {
      override fun configure() {
        // FakeTestService: has interface binding but missing TestFixture multibinding
        bind<TestService>().to<FakeTestService>()

        // FakeAnotherService: has interface binding but missing TestFixture multibinding
        bind<AnotherService>().to<FakeAnotherService>()
      }
    }

    val injector = Guice.createInjector(MultipleIssuesModule())
    val validator = TestFixtureBindingValidator(injector)

    val errors = validator.validate()

    assertThat(errors).hasSize(2)

    val testServiceError = errors.find { it.contains("FakeTestService") }
    val anotherServiceError = errors.find { it.contains("FakeAnotherService") }

    assertThat(testServiceError).contains("missing multibind<TestFixture>")
    assertThat(anotherServiceError).contains("missing multibind<TestFixture>")
  }

  @Test
  fun `validateAndLog does not throw exceptions`() {
    val injector = Guice.createInjector(MissingTestFixtureBindingModule())
    val validator = TestFixtureBindingValidator(injector)

    // Should not throw, just log
    validator.validateAndLog()
  }

  @Test
  fun `handles empty injector gracefully`() {
    val injector = Guice.createInjector()
    val validator = TestFixtureBindingValidator(injector)

    val errors = validator.validate()

    assertThat(errors).isEmpty()
  }

  // Additional interfaces for multi-interface test
  interface FirstInterface

  interface SecondInterface

  class MultiFakeService : FakeFixture(), FirstInterface, SecondInterface {
    val data by resettable { mutableListOf<String>() }
  }

  @Test
  fun `handles multiple interfaces bound to same implementation`() {
    class MultiInterfaceModule : KAbstractModule() {
      override fun configure() {
        bind<FirstInterface>().to<MultiFakeService>()
        bind<SecondInterface>().to<MultiFakeService>()
        // Missing: multibind<TestFixture>().to<MultiFakeService>()
      }
    }

    val injector = Guice.createInjector(MultiInterfaceModule())
    val validator = TestFixtureBindingValidator(injector)

    val errors = validator.validate()

    assertThat(errors).hasSize(1)
    assertThat(errors[0]).contains("MultiFakeService")
    assertThat(errors[0]).contains("FirstInterface")
    assertThat(errors[0]).contains("SecondInterface")
    assertThat(errors[0]).contains("missing multibind<TestFixture>")
  }
}
