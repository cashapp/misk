package misk.scope

import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.TypeLiteral
import com.google.inject.name.Named
import com.google.inject.name.Names
import misk.inject.keyOf
import misk.inject.toKey
import misk.inject.uninject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional
import javax.inject.Inject
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
    scope.enter(seedData).use { assertThat(foo.get()).isEqualTo("seed-value and bar and foo!") }
  }

  @Test
  fun doubleEnterScopeFails() {
    assertFailsWith<IllegalStateException> {
      val injector = Guice.createInjector(TestActionScopedProviderModule())
      injector.injectMembers(this)

      scope.enter(mapOf()).use { scope.enter(mapOf()).use { } }
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
  fun supportsParameterizedTypes() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    injector.injectMembers(this)

    val optionalStringKey = object : TypeLiteral<Optional<String>>() {}.toKey()

    val emptyOptionalSeedData: Map<Key<*>, Any> = mapOf(
      optionalStringKey to Optional.empty<String>(),
    )
    val emptyOptionalResult = scope.enter(emptyOptionalSeedData).use { optional.get() }
    assertThat(emptyOptionalResult).isEqualTo("empty")

    val presentOptionalSeedData: Map<Key<*>, Any> = mapOf(
      optionalStringKey to Optional.of("present"),
    )
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
      val seedData: Map<Key<*>, Any> = mapOf(
        keyOf<String>(Names.named("from-seed")) to "illegal-state"
      )
      scope.enter(seedData).use { zed.get() }
    }
  }
}
