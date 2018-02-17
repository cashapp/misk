package misk.scope

import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.name.Named
import com.google.inject.name.Names
import misk.inject.keyOf
import misk.inject.uninject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.inject.Inject

class ActionScopedTest {
  @Inject @Named("foo")
  private lateinit var foo: ActionScoped<String>

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
    scope.enter(seedData)
        .use {
          assertThat(foo.get()).isEqualTo("seed-value and bar and foo!")
        }
  }

  @Test
  fun doubleEnterScopeFails() {
    assertThrows(IllegalStateException::class.java) {
      val injector = Guice.createInjector(TestActionScopedProviderModule())
      injector.injectMembers(this)

      scope.enter(mapOf())
          .use {
            scope.enter(mapOf())
                .use { }
          }
    }
  }

  @Test
  fun resolveOutsideOfScopeFails() {
    assertThrows(IllegalStateException::class.java) {
      val injector = Guice.createInjector(TestActionScopedProviderModule())
      injector.injectMembers(this)

      foo.get()
    }
  }

  @Test
  fun seedDataNotFoundFails() {
    assertThrows(IllegalStateException::class.java) {
      val injector = Guice.createInjector(TestActionScopedProviderModule())
      injector.injectMembers(this)

      // NB(mmihic): Seed data not specified
      scope.enter(mapOf())
          .use { foo.get() }
    }
  }
}
