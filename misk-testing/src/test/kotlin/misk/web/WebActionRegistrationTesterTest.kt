package misk.web

import com.google.inject.Guice
import misk.inject.KAbstractModule
import misk.web.actions.WebAction
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import jakarta.inject.Inject

class WebActionRegistrationTesterTest {

  @Test
  fun `passes when all actions are registered`() {
    val injector = Guice.createInjector(
      object : KAbstractModule() {
        override fun configure() {
          install(WebActionModule.create<RegisteredAction>())
        }
      }
    )

    WebActionRegistrationTester.assertAllWebActionsRegistered(
      injector,
      WebActionRegistrationTester.Options(
        basePackages = listOf("misk.web"),
        excludePredicate = { actionClass ->
          // Exclude test actions from other test classes in this package
          actionClass != RegisteredAction::class
        },
      )
    )
  }

  @Test
  fun `fails when actions are not registered`() {
    val injector = Guice.createInjector(
      object : KAbstractModule() {
        override fun configure() {
          // Intentionally not registering UnregisteredAction
        }
      }
    )

    assertThatThrownBy {
      WebActionRegistrationTester.assertAllWebActionsRegistered(
        injector,
        WebActionRegistrationTester.Options(
          basePackages = listOf("misk.web"),
          excludePredicate = { actionClass ->
            // Only check UnregisteredAction for this test
            actionClass != UnregisteredAction::class
          },
        )
      )
    }
      .isInstanceOf(AssertionError::class.java)
      .hasMessageContaining("UnregisteredAction")
      .hasMessageContaining("WebActionModule.create<UnregisteredAction>")
  }

  @Test
  fun `excludes abstract classes`() {
    val injector = Guice.createInjector(
      object : KAbstractModule() {
        override fun configure() {
          // No actions registered
        }
      }
    )

    // Should not fail because AbstractAction is abstract
    WebActionRegistrationTester.assertAllWebActionsRegistered(
      injector,
      WebActionRegistrationTester.Options(
        basePackages = listOf("misk.web"),
        excludePredicate = { actionClass ->
          // Exclude all non-abstract test actions
          actionClass != AbstractAction::class
        },
      )
    )
  }

  @Test
  fun `respects custom exclude predicate`() {
    val injector = Guice.createInjector(
      object : KAbstractModule() {
        override fun configure() {
          // Intentionally not registering ExcludedAction
        }
      }
    )

    // Should pass because ExcludedAction is excluded via predicate
    WebActionRegistrationTester.assertAllWebActionsRegistered(
      injector,
      WebActionRegistrationTester.Options(
        basePackages = listOf("misk.web"),
        excludePredicate = { actionClass ->
          // Exclude all test actions
          true
        },
      )
    )
  }

  @Test
  fun `error message includes registration module hint`() {
    val injector = Guice.createInjector(
      object : KAbstractModule() {
        override fun configure() {
          // Intentionally not registering
        }
      }
    )

    assertThatThrownBy {
      WebActionRegistrationTester.assertAllWebActionsRegistered(
        injector,
        WebActionRegistrationTester.Options(
          basePackages = listOf("misk.web"),
          excludePredicate = { actionClass ->
            actionClass != UnregisteredAction::class
          },
          registrationModuleHint = "MyWebModule",
        )
      )
    }
      .isInstanceOf(AssertionError::class.java)
      .hasMessageContaining("MyWebModule")
  }

  // Test actions used for verification

  class RegisteredAction @Inject constructor() : WebAction {
    @Get("/registered")
    fun get(): String = "registered"
  }

  class UnregisteredAction @Inject constructor() : WebAction {
    @Get("/unregistered")
    fun get(): String = "unregistered"
  }

  abstract class AbstractAction : WebAction

  class ExcludedAction @Inject constructor() : WebAction {
    @Get("/excluded")
    fun get(): String = "excluded"
  }
}
