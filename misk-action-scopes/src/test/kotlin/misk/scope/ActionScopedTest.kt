package misk.scope

import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.TypeLiteral
import com.google.inject.name.Named
import com.google.inject.name.Names
import jakarta.inject.Inject
import java.util.Optional
import kotlin.concurrent.thread
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import misk.inject.keyOf
import misk.inject.toKey
import misk.inject.uninject
import misk.scope.TestActionScopedProviderModule.TestListener
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class ActionScopedTest {
  @Inject @Named("foo") private lateinit var foo: ActionScoped<String>

  @Inject @Named("zed") private lateinit var zed: ActionScoped<String>

  @Inject @Named("optional") private lateinit var optional: ActionScoped<String>

  @Inject @Named("nullable-foo") private lateinit var nullableFoo: ActionScoped<String?>

  @Inject @Named("nullable-based-on-foo") private lateinit var nullableBasedOnFoo: ActionScoped<String?>

  @Inject @Named("constant") private lateinit var constantString: ActionScoped<String>

  @Inject @Named("constant") private lateinit var optionalConstantString: ActionScoped<Optional<String>>

  @Inject @Named("counting") private lateinit var countingString: ActionScoped<String>

  @Inject private lateinit var scope: ActionScope

  @Inject private lateinit var testListener: TestListener

  @BeforeEach
  fun clearInjections() {
    uninject(this)
  }

  @Test
  fun resolvedChainedActionScoped() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    injector.injectMembers(this)

    val seedData: Map<Key<*>, Any> = mapOf(keyOf<String>(Names.named("from-seed")) to "seed-value")

    scope.enter(seedData).use { assertThat(foo.get()).isEqualTo("seed-value and bar and foo!") }
  }

  @Test
  fun overrideActionScopedProvider() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    injector.injectMembers(this)

    val seedData: Map<Key<*>, Any> = mapOf(keyOf<String>(Names.named("from-seed")) to "seed-value")

    val providerOverride =
      object : ActionScopedProvider<String> {
        override fun get(): String = "overridden-bar"
      }

    scope.create(seedData, mapOf(keyOf<String>(Names.named("bar")) to providerOverride)).inScope {
      assertThat(foo.get()).isEqualTo("overridden-bar and foo!")
    }
  }

  @Test
  fun doubleEnterScopeFails() {
    assertFailsWith<IllegalStateException> {
      val injector = Guice.createInjector(TestActionScopedProviderModule())
      injector.injectMembers(this)

      scope.enter(mapOf()).use { scope.enter(mapOf()).use {} }
    }
  }

  @Test
  fun resolveOutsideOfScopeFails() {
    assertFailsWith<IllegalStateException> {
      val injector = Guice.createInjector(TestActionScopedProviderModule())
      injector.injectMembers(this)

      foo.get()
    }
  }

  @Test
  fun seedDataNotFoundFails() {
    assertFailsWith<IllegalStateException> {
      val injector = Guice.createInjector(TestActionScopedProviderModule())
      injector.injectMembers(this)

      // NB(mmihic): Seed data not specified
      scope.enter(mapOf()).use { foo.get() }
    }
  }

  @Test
  fun supportsNullableValues() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    injector.injectMembers(this)

    val seedData: Map<Key<*>, Any> = mapOf(keyOf<String>(Names.named("from-seed")) to "null")
    val result = scope.enter(seedData).use { nullableFoo.get() }
    assertThat(result).isNull()
  }

  @Test
  fun supportsReturningConstants() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    injector.injectMembers(this)

    scope.enter(mapOf()).use {
      assertThat(constantString.get()).isEqualTo("constant-value")
      assertThat(optionalConstantString.get()).isEqualTo(Optional.of("constant-value"))

      // Make sure the same object is returned
      assertThat(constantString.get()).isSameAs(constantString.get())
      assertThat(optionalConstantString.get()).isSameAs(optionalConstantString.get())
    }
  }

  @Test
  fun supportsParameterizedTypes() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    injector.injectMembers(this)

    val optionalStringKey = object : TypeLiteral<Optional<String>>() {}.toKey()

    val emptyOptionalSeedData: Map<Key<*>, Any> = mapOf(optionalStringKey to Optional.empty<String>())
    val emptyOptionalResult = scope.enter(emptyOptionalSeedData).use { optional.get() }
    assertThat(emptyOptionalResult).isEqualTo("empty")

    val presentOptionalSeedData: Map<Key<*>, Any> = mapOf(optionalStringKey to Optional.of("present"))
    val presentOptionalResult = scope.enter(presentOptionalSeedData).use { optional.get() }
    assertThat(presentOptionalResult).isEqualTo("present")
  }

  @Test
  fun supportsCascadingNullableValues() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    injector.injectMembers(this)

    val seedData: Map<Key<*>, Any> = mapOf(keyOf<String>(Names.named("from-seed")) to "null")
    val result = scope.enter(seedData).use { nullableBasedOnFoo.get() }
    assertThat(result).isNull()
  }

  @Test
  fun providerExceptionsPropagate() {
    assertFailsWith<IllegalStateException> {
      val injector = Guice.createInjector(TestActionScopedProviderModule())
      injector.injectMembers(this)

      // NB(mmihic): Seed data set to a value that causes zed resolution to fail
      // with a user-defined exception
      val seedData: Map<Key<*>, Any> = mapOf(keyOf<String>(Names.named("from-seed")) to "illegal-state")
      scope.enter(seedData).use { zed.get() }
    }
  }

  @Test
  fun `propagate action scope to coroutines scope`() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    injector.injectMembers(this)

    val seedData: Map<Key<*>, Any> = mapOf(keyOf<String>(Names.named("from-seed")) to "seed-value")

    scope.enter(seedData).use { actionScope ->
      runBlocking(actionScope.asContextElement()) { assertThat(foo.get()).isEqualTo("seed-value and bar and foo!") }
    }
  }

  @Test
  fun `throws if asContextElement is not call within a scope`() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    injector.injectMembers(this)

    assertFailsWith<IllegalStateException> { scope.asContextElement() }
  }

  @Test
  fun `allow getting scope things from another thread`() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    injector.injectMembers(this)

    val seedData: Map<Key<*>, Any> = mapOf(keyOf<String>(Names.named("from-seed")) to "seed-value")

    // exhibit A: trying to access scoped things in a new thread results in exceptions
    scope.enter(seedData).use { _ ->
      var thrown: Throwable? = null

      thread {
          try {
            assertThat(foo.get()).isEqualTo("seed-value and bar and foo!")
          } catch (t: Throwable) {
            thrown = t
          }
        }
        .join()
      assertThat(thrown).isNotNull
    }

    // exhibit B: trying to access scoped things in a new thread can work, if you take
    // a snapshot of the scope and use it to instantiate a scope in the new thread.
    scope.enter(seedData).use { actionScope ->
      var thrown: Throwable? = null

      val snapshot = actionScope.snapshotActionScope()
      thread {
          try {
            actionScope.enter(snapshot).use { assertThat(foo.get()).isEqualTo("seed-value and bar and foo!") }
          } catch (t: Throwable) {
            thrown = t
          }
        }
        .join()
      assertThat(thrown).isNull()
    }
  }

  @Test
  fun `allow getting scope things from scope`() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    injector.injectMembers(this)

    val seedData: Map<Key<*>, Any> = mapOf(keyOf<String>(Names.named("from-seed")) to "seed-value")

    // exhibit A: trying to access scoped things in a new thread results in exceptions
    scope.enter(seedData).use { _ ->
      var thrown: Throwable? = null

      thread {
          try {
            assertThat(foo.get()).isEqualTo("seed-value and bar and foo!")
          } catch (t: Throwable) {
            thrown = t
          }
        }
        .join()
      assertThat(thrown).isNotNull
    }

    // exhibit B: trying to access scoped things in a new thread can work, if you take
    // a snapshot of the scope and use it to instantiate a scope in the new thread.
    scope.enter(seedData).use { actionScope ->
      var thrown: Throwable? = null

      val instance = actionScope.snapshotActionScopeInstance()
      thread {
          try {
            actionScope.enter(instance).use { assertThat(foo.get()).isEqualTo("seed-value and bar and foo!") }
          } catch (t: Throwable) {
            thrown = t
          }
        }
        .join()
      assertThat(thrown).isNull()
    }
  }

  @Test
  fun `providers are only called once per scope regardless of how many threads it propagates to`() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    injector.injectMembers(this)

    scope.create(mapOf()).inScope {
      val instance = scope.snapshotActionScopeInstance()
      repeat(3) {
        thread { instance.inScope { assertThat(countingString.get()).isEqualTo("Called CountingProvider 1 time(s)") } }
          .join()
      }
      assertThat(countingString.get()).isEqualTo("Called CountingProvider 1 time(s)")
    }
  }

  @Test
  fun `listeners are called before the scope closes`() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    injector.injectMembers(this)

    assertThat(testListener.result).isNull()

    scope.create(mapOf()).inScope {
      // We don't have to do anything in the scope, just call inScope() so that close() is called. The listener is
      // then triggered, setting the result field to an action scoped value, to show we're still in an action scope.
    }

    assertThat(testListener.result).isEqualTo("constant-value")
  }

  @Test
  fun `calling close on an already closed scope does nothing`() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    injector.injectMembers(this)

    // Make sure that calling onClose on the listener directly throws an exception because we're not in an action scope
    assertThrows<IllegalStateException> { testListener.onClose() }

    // Make sure that closing the scope when it isn't open doesn't call the listeners which would throw the exception
    scope.close()
  }
}
