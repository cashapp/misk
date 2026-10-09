package misk.testing

import com.google.inject.testing.fieldbinder.Bind
import jakarta.inject.Inject
import misk.inject.ReusableTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener

/**
 * Tests the injector-reuse guard in [MiskTestExtension]: a test class with `@Bind` fields must never silently reuse an
 * injector that was cached for a different test class, because only the creating class's `@Bind` fields are baked into
 * the cached injector.
 *
 * The fixture classes are run programmatically below through the JUnit launcher. They share cache keys via
 * structurally-equal [ReusableTestModule]s, which is what makes them collide under reuse. Each `@Test` method here is
 * written to pass regardless of the order JUnit runs them in, since the injector cache is static for the whole JVM.
 */
class InjectorReuseTest {

  @Test
  fun `reuse across classes is allowed when the reusing class has no Bind fields`() {
    setReuseInjector(true)
    try {
      runLauncher(NoBindOwner::class.java).assertAllPassed()
      runLauncher(NoBindReuser::class.java).assertAllPassed()
    } finally {
      setReuseInjector(false)
    }
  }

  @Test
  fun `reuse is refused when a class with Bind fields reuses another class's injector`() {
    setReuseInjector(true)
    try {
      runLauncher(NoBindOwner2::class.java).assertAllPassed()
      val summary = runLauncher(BindReuser::class.java).summary
      assertThat(summary.totalFailureCount).isPositive()
      val failure = summary.failures.first()
      assertThat(failure.exception).isInstanceOf(IllegalStateException::class.java)
      assertThat(failure.exception)
        .hasMessageContaining(BindReuser::class.java.name)
        .hasMessageContaining(NoBindOwner2::class.java.name)
        .hasMessageContaining("@Bind")
    } finally {
      setReuseInjector(false)
    }
  }

  @Test
  fun `a class with Bind fields gets its own injector when it is first for its key`() {
    setReuseInjector(true)
    try {
      // BindOwner uses a module key no other class shares, so it always creates the injector for
      // its key and its @Bind fields must apply.
      runLauncher(BindOwner::class.java).assertAllPassed()
    } finally {
      setReuseInjector(false)
    }
  }

  private fun setReuseInjector(enabled: Boolean) {
    if (enabled) {
      // The environment variable takes precedence over the system property; fail if it is set so
      // this test never silently tests the wrong path.
      assertThat(System.getenv("MISK_TEST_REUSE_INJECTOR")).isNull()
      System.setProperty("MISK_TEST_REUSE_INJECTOR", "true")
    } else {
      System.clearProperty("MISK_TEST_REUSE_INJECTOR")
    }
  }

  private fun runLauncher(testClass: Class<*>): SummaryGeneratingListener {
    val listener = SummaryGeneratingListener()
    val request = LauncherDiscoveryRequestBuilder.request().selectors(selectClass(testClass)).build()
    LauncherFactory.create().execute(request, listener)
    return listener
  }

  private fun SummaryGeneratingListener.assertAllPassed() {
    val failures = summary.failures
    assertThat(failures)
      .overridingErrorMessage(
        "launcher run failures: " +
          failures.joinToString("\n") {
            it.exception.toString() + "\n" + it.exception.stackTrace.take(12).joinToString("\n")
          }
      )
      .isEmpty()
  }

  /** A [BoundThing] bound with `@Bind` in the fixture classes below. */
  private class BoundThing

  /**
   * Structurally equal to every other instance of itself, so all fixtures using it share one injector-reuse cache key.
   */
  private class SharedKeyModule : ReusableTestModule() {
    override fun configure() {
      bind<String>().toInstance("shared")
    }
  }

  /** Only used by [BindOwner]; structurally unique to it, so it never shares a cache entry. */
  private class BindOwnerKeyModule : ReusableTestModule() {
    override fun configure() {
      bind<String>().toInstance("shared")
    }
  }

  @MiskTest
  internal class NoBindOwner {
    @Suppress("unused") @MiskTestModule private val module = SharedKeyModule()

    @Inject private lateinit var injected: String

    @Test
    fun `owner gets its bindings`() {
      assertThat(injected).isEqualTo("shared")
    }
  }

  @MiskTest
  internal class NoBindReuser {
    @Suppress("unused") @MiskTestModule private val module = SharedKeyModule()

    @Inject private lateinit var injected: String

    @Test
    fun `reuser without Bind fields reuses the owner's injector`() {
      assertThat(injected).isEqualTo("shared")
    }
  }

  @MiskTest
  internal class NoBindOwner2 {
    @Suppress("unused") @MiskTestModule private val module = SharedKeyModule()

    @Inject private lateinit var injected: String

    @Test
    fun `owner gets its bindings`() {
      assertThat(injected).isEqualTo("shared")
    }
  }

  @MiskTest
  internal class BindReuser {
    @Suppress("unused") @MiskTestModule private val module = SharedKeyModule()

    @Bind private val thing: BoundThing = BoundThing()

    @Inject private lateinit var injectedThing: BoundThing

    @Test
    fun `never reached - the extension must refuse reusing another class's injector`() {
      assertThat(injectedThing).isSameAs(thing)
    }
  }

  @MiskTest
  internal class BindOwner {
    @Suppress("unused") @MiskTestModule private val module = BindOwnerKeyModule()

    @Bind private val thing: BoundThing = BoundThing()

    @Inject private lateinit var injectedThing: BoundThing

    @Test
    fun `first class for its key gets its Bind fields applied`() {
      assertThat(injectedThing).isSameAs(thing)
    }
  }
}
