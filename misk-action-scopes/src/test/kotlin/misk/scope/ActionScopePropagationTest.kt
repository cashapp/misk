package misk.scope

import com.google.common.util.concurrent.MoreExecutors
import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.name.Named
import com.google.inject.name.Names
import misk.inject.keyOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.reflect.KFunction

internal class ActionScopePropagationTest {
  class Tester {
    @Inject
    @Named("foo") lateinit var foo: ActionScoped<String>

    fun fooValue(): String = foo.get()
  }

  private val singleThreadExecutor = Executors.newSingleThreadExecutor()
  private val directExecutor = MoreExecutors.newDirectExecutorService()

  @Test
  fun propagatesScopeOnSameThreadForCallable() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    val scope = injector.getInstance(ActionScope::class.java)
    val tester = injector.getInstance(Tester::class.java)

    val seedData: Map<Key<*>, Any> = mapOf(
      keyOf<String>(Names.named("from-seed")) to "my seed data"
    )

    val callable = scope.enter(seedData).use {
      scope.propagate(Callable { tester.fooValue() })
    }

    scope.enter(seedData).use {
      // Submit to same thread after we've already entered the scope
      val result = directExecutor.submit(callable).get()
      assertThat(result).isEqualTo("my seed data and bar and foo!")
    }
  }

  @Test
  fun propagatesScopeOnNewThreadForCallable() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    val scope = injector.getInstance(ActionScope::class.java)
    val tester = injector.getInstance(Tester::class.java)

    val seedData: Map<Key<*>, Any> = mapOf(
      keyOf<String>(Names.named("from-seed")) to "my seed data"
    )

    val callable = scope.enter(seedData).use {
      scope.propagate(Callable { tester.fooValue() })
    }

    // Submit to other thread after we've exited the scope
    val result = singleThreadExecutor.submit(callable).get()
    assertThat(result).isEqualTo("my seed data and bar and foo!")
  }

  @Test
  fun propagatesScopeOnSameThreadForKFunction() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    val scope = injector.getInstance(ActionScope::class.java)
    val tester = injector.getInstance(Tester::class.java)

    val seedData: Map<Key<*>, Any> = mapOf(
      keyOf<String>(Names.named("from-seed")) to "my seed data"
    )

    // Propagate on the the KCallable directly
    val f: KFunction<String> = tester::fooValue
    val callable = scope.enter(seedData).use {
      scope.propagate(f)
    }

    scope.enter(seedData).use {
      // Submit to same thread after we've already entered the scope
      val result = directExecutor.submit(
        Callable {
          callable.call()
        }
      ).get()
      assertThat(result).isEqualTo("my seed data and bar and foo!")
    }
  }

  @Test
  fun propagatesScopeOnNewThreadForKFunction() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    val scope = injector.getInstance(ActionScope::class.java)
    val tester = injector.getInstance(Tester::class.java)

    val seedData: Map<Key<*>, Any> = mapOf(
      keyOf<String>(Names.named("from-seed")) to "my seed data"
    )

    // Propagate on the the KCallable directly
    val f: KFunction<String> = tester::fooValue
    val callable = scope.enter(seedData).use {
      scope.propagate(f)
    }

    // Submit to other thread after we've exited the scope
    val result = singleThreadExecutor.submit(
      Callable {
        callable.call()
      }
    ).get()
    assertThat(result).isEqualTo("my seed data and bar and foo!")
  }

  @Test
  fun propagatesScopeOnSameThreadForLambda() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    val scope = injector.getInstance(ActionScope::class.java)
    val tester = injector.getInstance(Tester::class.java)

    val seedData: Map<Key<*>, Any> = mapOf(
      keyOf<String>(Names.named("from-seed")) to "my seed data"
    )

    // Propagate on a lambda directly
    val function = scope.enter(seedData).use {
      scope.propagate { tester.fooValue() }
    }

    scope.enter(seedData).use {
      // Submit to same thread after we've already entered the scope
      val result = directExecutor.submit(Callable { function() }).get()
      assertThat(result).isEqualTo("my seed data and bar and foo!")
    }
  }

  @Test
  fun propagatesScopeOnNewThreadForLambda() {
    val injector = Guice.createInjector(TestActionScopedProviderModule())
    val scope = injector.getInstance(ActionScope::class.java)
    val tester = injector.getInstance(Tester::class.java)

    val seedData: Map<Key<*>, Any> = mapOf(
      keyOf<String>(Names.named("from-seed")) to "my seed data"
    )

    // Propagate on a lambda directly
    val function = scope.enter(seedData).use {
      scope.propagate { tester.fooValue() }
    }

    // Submit to other thread after we've exited the scope
    val result = singleThreadExecutor.submit(Callable { function() }).get()
    assertThat(result).isEqualTo("my seed data and bar and foo!")
  }
}
