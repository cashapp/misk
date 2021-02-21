package misk.scope.executor

import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.Provides
import com.google.inject.name.Named
import com.google.inject.name.Names
import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.scope.ActionScope
import misk.scope.ActionScoped
import misk.scope.TestActionScopedProviderModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.test.assertFailsWith

internal class ActionScopedExecutorServiceTest {
  class Tester {
    @Inject
    @Named("foo") lateinit var foo: ActionScoped<String>

    fun fooValue() = foo.get()
  }

  @Test
  fun propagatesScopeIfInScope() {
    val injector = Guice.createInjector(
      TestActionScopedProviderModule(),
      ActionScopedExecutorServiceModule()
    )

    val tester = injector.getInstance(Tester::class.java)
    val executor = injector.getInstance(ExecutorService::class.java)
    val scope = injector.getInstance(ActionScope::class.java)

    val seedData: Map<Key<*>, Any> = mapOf(
      keyOf<String>(Names.named("from-seed")) to "my seed data"
    )

    val future = scope.enter(seedData).use {
      executor.submit(Callable { tester.fooValue() })
    }

    assertThat(future.get()).isEqualTo("my seed data and bar and foo!")
  }

  @Test
  fun doesNotPropagateScopeIfNotInScope() {
    assertFailsWith<IllegalStateException> {
      val injector = Guice.createInjector(
        TestActionScopedProviderModule(),
        ActionScopedExecutorServiceModule()
      )

      val tester = injector.getInstance(Tester::class.java)
      val executor = injector.getInstance(ExecutorService::class.java)

      executor.submit(Callable { tester.fooValue() })
    }
  }

  class ActionScopedExecutorServiceModule : KAbstractModule() {
    override fun configure() {}

    @Provides
    @Singleton
    fun provideExecutorService(scope: ActionScope): ExecutorService {
      return ActionScopedExecutorService(Executors.newSingleThreadExecutor(), scope)
    }
  }
}
