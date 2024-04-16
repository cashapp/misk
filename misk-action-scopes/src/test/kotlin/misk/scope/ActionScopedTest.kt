package misk.scope

import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.TypeLiteral
import com.google.inject.name.Named
import com.google.inject.name.Names
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import misk.inject.keyOf
import misk.inject.toKey
import misk.inject.uninject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.concurrent.thread
import kotlin.test.assertFailsWith

internal class ActionScopedTest {
  @Inject @Named("foo")
  private lateinit var foo: ActionScoped<String>

  @Inject @Named("zed")
  private lateinit var zed: ActionScoped<String>

  @Inject @Named("optional")
  private lateinit var optional: ActionScoped<String>

  @Inject @Named("nullable-foo")
  private lateinit var nullableFoo: ActionScoped<String?>

  @Inject @Named("nullable-based-on-foo")
  private lateinit var nullableBasedOnFoo: ActionScoped<String?>

  @Inject @Named("constant")
  private lateinit var constantString: ActionScoped<String>

  @Inject @Named("constant")
  private lateinit var optionalConstantString: ActionScoped<Optional<String>>

  @Inject @Named("counting")
  private lateinit var countingString: ActionScoped<String>

  @Inject private lateinit var scope: ActionScope

  @BeforeEach
  fun clearInjections() {
    uninject(this)
  }

  @Test
  fun resolvedChainedActionScoped() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    injector.injectMembers(this)

    val seedData: Map<Key<*>, Any> = mapOf(
      keyOf<String>(Names.named("from-seed")) to "seed-value"

    )
    scope.create(seedData).inScope { assertThat(foo.get()).isEqualTo("seed-value and bar and foo!") }
  }

  @Test
  fun doubleEnterScopeFails() {
    assertFailsWith<IllegalStateException> {
      val injector = Guice.createInjector(TestActionScopedProviderModule())
      injector.injectMembers(this)

      scope.create(mapOf()).inScope { scope.create(mapOf()) }
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
      scope.create(mapOf()).inScope { foo.get() }
    }
  }

  @Test
  fun supportsNullableValues() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    injector.injectMembers(this)

    val seedData: Map<Key<*>, Any> = mapOf(keyOf<String>(Names.named("from-seed")) to "null")
    val result = scope.create(seedData).inScope { nullableFoo.get() }
    assertThat(result).isNull()
  }

  @Test
  fun supportsReturningConstants() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    injector.injectMembers(this)

    scope.create(mapOf()).inScope {
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

    val emptyOptionalSeedData: Map<Key<*>, Any> = mapOf(
      optionalStringKey to Optional.empty<String>(),
    )
    val emptyOptionalResult = scope.create(emptyOptionalSeedData).inScope { optional.get() }
    assertThat(emptyOptionalResult).isEqualTo("empty")

    val presentOptionalSeedData: Map<Key<*>, Any> = mapOf(
      optionalStringKey to Optional.of("present"),
    )
    val presentOptionalResult = scope.create(presentOptionalSeedData).inScope { optional.get() }
    assertThat(presentOptionalResult).isEqualTo("present")
  }

  @Test
  fun supportsCascadingNullableValues() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    injector.injectMembers(this)

    val seedData: Map<Key<*>, Any> = mapOf(keyOf<String>(Names.named("from-seed")) to "null")
    val result = scope.create(seedData).inScope { nullableBasedOnFoo.get() }
    assertThat(result).isNull()
  }

  @Test
  fun providerExceptionsPropagate() {
    assertFailsWith<IllegalStateException> {
      val injector = Guice.createInjector(TestActionScopedProviderModule())
      injector.injectMembers(this)

      // NB(mmihic): Seed data set to a value that causes zed resolution to fail
      // with a user-defined exception
      val seedData: Map<Key<*>, Any> = mapOf(
        keyOf<String>(Names.named("from-seed")) to "illegal-state"
      )
      scope.create(seedData).inScope { zed.get() }
    }
  }

  @Test
  fun `propagate action scope to coroutines scope`() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    injector.injectMembers(this)

    val seedData: Map<Key<*>, Any> = mapOf(
      keyOf<String>(Names.named("from-seed")) to "seed-value"

    )
    scope.create(seedData).inScope {
      runBlocking(scope.asContextElement()) {
        assertThat(foo.get()).isEqualTo("seed-value and bar and foo!")
      }
    }
  }

  @Test
  fun `throws if asContextElement is not call within a scope`() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    injector.injectMembers(this)

    assertFailsWith<IllegalStateException> {
      scope.asContextElement()
    }
  }

  @Test
  fun `allow getting scope things from another thread`() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    injector.injectMembers(this)

    val seedData: Map<Key<*>, Any> = mapOf(
      keyOf<String>(Names.named("from-seed")) to "seed-value"
    )

    // exhibit A: trying to access scoped things in a new thread results in exceptions
    scope.create(seedData).inScope {
      var thrown: Throwable? = null

      thread {
        try {
          assertThat(foo.get()).isEqualTo("seed-value and bar and foo!")
        } catch (t: Throwable) {
          thrown = t
        }
      }.join()
      assertThat(thrown).isNotNull
    }

    // exhibit B: trying to access scoped things in a new thread can work, if you take
    // a snapshot of the scope and use it to instantiate a scope in the new thread.
    scope.create(seedData).inScope {
      var thrown: Throwable? = null

      val instance = scope.snapshotActionScopeInstance()
      thread {
        try {
          instance.inScope {
            assertThat(foo.get()).isEqualTo("seed-value and bar and foo!")
          }
        } catch (t: Throwable) {
          thrown = t
        }
      }.join()
      assertThat(thrown).isNull()
    }
  }

  @Test
  fun `allow getting scope things from scope`() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    injector.injectMembers(this)

    val seedData: Map<Key<*>, Any> = mapOf(
      keyOf<String>(Names.named("from-seed")) to "seed-value"
    )

    // exhibit A: trying to access scoped things in a new thread results in exceptions
    scope.create(seedData).inScope {
      var thrown: Throwable? = null

      thread {
        try {
          assertThat(foo.get()).isEqualTo("seed-value and bar and foo!")
        } catch (t: Throwable) {
          thrown = t
        }
      }.join()
      assertThat(thrown).isNotNull
    }

    // exhibit B: trying to access scoped things in a new thread can work, if you take
    // a snapshot of the scope and use it to instantiate a scope in the new thread.
    scope.create(seedData).inScope {
      var thrown: Throwable? = null

      val instance = scope.snapshotActionScopeInstance()
      thread {
        try {
          instance.inScope {
            assertThat(foo.get()).isEqualTo("seed-value and bar and foo!")
          }
        } catch (t: Throwable) {
          thrown = t
        }
      }.join()
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
        thread {
          instance.inScope {
            assertThat(countingString.get()).isEqualTo("Called CountingProvider 1 time(s)")
          }
        }.join()
      }
      assertThat(countingString.get()).isEqualTo("Called CountingProvider 1 time(s)")
    }
  }
}
