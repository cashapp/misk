package misk.scope.coroutine

import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.name.Named
import com.google.inject.name.Names
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import misk.inject.getInstance
import misk.inject.keyOf
import misk.scope.ActionScope
import misk.scope.ActionScoped
import misk.scope.TestActionScopedProviderModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ActionScopedCoroutineTest {
  class Tester {
    @Inject @Named("foo") lateinit var foo: ActionScoped<String>

    fun fooValue() = foo.get()
  }

  @Test
  fun propagatesIfInScope() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())

    val tester = injector.getInstance<Tester>()
    val scope = injector.getInstance(ActionScope::class.java)

    val seedData: Map<Key<*>, Any> = mapOf(keyOf<String>(Names.named("from-seed")) to "my seed data")

    val value = scope.enter(seedData).use { scope.runBlocking { tester.fooValue() } }

    assertThat(value).isEqualTo("my seed data and bar and foo!")
  }

  @Test
  fun propagatesOnOtherDispatcher() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())

    val tester = injector.getInstance<Tester>()
    val scope = injector.getInstance(ActionScope::class.java)

    val seedData: Map<Key<*>, Any> = mapOf(keyOf<String>(Names.named("from-seed")) to "my seed data")

    val value = scope.enter(seedData).use { scope.runBlocking(Dispatchers.IO) { tester.fooValue() } }

    assertThat(value).isEqualTo("my seed data and bar and foo!")
  }

  @Test
  fun doesNotPropagateOutsideOfScope() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())

    val scope = injector.getInstance(ActionScope::class.java)

    val inScope = scope.runBlocking { scope.inScope() }

    assertThat(inScope).isFalse()
  }

  @Test
  fun doesNotPropagateOutsideOfScopeOnOtherDispatcher() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())

    val scope = injector.getInstance(ActionScope::class.java)

    val inScope = scope.runBlocking(Dispatchers.IO) { scope.inScope() }

    assertThat(inScope).isFalse()
  }
}
