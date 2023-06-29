package misk.web.concurrencylimits

import com.netflix.concurrency.limits.Limit
import com.netflix.concurrency.limits.limit.AIMDLimit
import com.netflix.concurrency.limits.limit.FixedLimit
import com.netflix.concurrency.limits.limit.Gradient2Limit
import com.netflix.concurrency.limits.limit.GradientLimit
import com.netflix.concurrency.limits.limit.SettableLimit
import com.netflix.concurrency.limits.limit.VegasLimit
import com.netflix.concurrency.limits.limiter.SimpleLimiter
import misk.MiskTestingServiceModule
import misk.asAction
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.ConcurrencyLimiterConfig
import misk.web.DispatchMechanism
import misk.web.Get
import misk.web.MiskWebModule
import misk.web.WebConfig
import misk.web.actions.WebAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import wisp.deployment.Deployment
import wisp.deployment.TESTING
import javax.inject.Inject

@MiskTest(startService = true)
class ConcurrencyLimitsStrategyTest {
  private lateinit var strategy: ConcurrencyLimiterStrategy
  private var maxConcurrency: Int? = null

  @MiskTestModule
  val module = object : KAbstractModule() {
    override fun configure() {
      bind<Deployment>().toInstance(TESTING)

      install(MiskTestingServiceModule())

      newMultibinder<ConcurrencyLimiterFactory>()
      install(
        MiskWebModule(
          WebConfig(
            port = 0,
            concurrency_limiter = ConcurrencyLimiterConfig(
              strategy = strategy,
              max_concurrency = maxConcurrency,
            )
          )
        )
      )
    }
  }

  @Inject private lateinit var limiterFactories: List<ConcurrencyLimiterFactory>
  @Inject private lateinit var limit: Limit

  private class HelloAction : WebAction {
    @Get("/hello")
    fun call(): String = "hello"
  }

  private fun List<ConcurrencyLimiterFactory>.createLimit() =
    first().create(HelloAction::call.asAction(DispatchMechanism.POST))

  @Nested
  inner class VegasStrategyTest {
    init {
      strategy = ConcurrencyLimiterStrategy.VEGAS
    }

    @Test
    fun `limiter factory is bound`() {
      assertThat(limit).isInstanceOf(VegasLimit::class.java)
      assertThat(limiterFactories).hasSize(1)
      assertThat(limiterFactories.createLimit()).isInstanceOf(SimpleLimiter::class.java)
    }
  }

  @Nested
  inner class GradientStrategyTest {
    init {
      strategy = ConcurrencyLimiterStrategy.GRADIENT
    }

    @Test
    fun `limiter factory is bound`() {
      assertThat(limit).isInstanceOf(GradientLimit::class.java)
      assertThat(limiterFactories).hasSize(1)
      assertThat(limiterFactories.createLimit()).isInstanceOf(SimpleLimiter::class.java)
    }
  }

  @Nested
  inner class Gradient2StrategyTest {
    init {
      strategy = ConcurrencyLimiterStrategy.GRADIENT2
    }

    @Test
    fun `limiter factory is bound`() {
      assertThat(limit).isInstanceOf(Gradient2Limit::class.java)
      assertThat(limiterFactories).hasSize(1)
      assertThat(limiterFactories.createLimit()).isInstanceOf(SimpleLimiter::class.java)
    }
  }

  @Nested
  inner class AimdStrategyTest {
    init {
      strategy = ConcurrencyLimiterStrategy.AIMD
    }

    @Test
    fun `limiter factory is bound`() {
      assertThat(limit).isInstanceOf(AIMDLimit::class.java)
      assertThat(limiterFactories).hasSize(1)
      assertThat(limiterFactories.createLimit()).isInstanceOf(SimpleLimiter::class.java)
    }
  }

  @Nested
  inner class SettableStrategyTest {
    init {
      strategy = ConcurrencyLimiterStrategy.SETTABLE
      maxConcurrency = 3
    }

    @Test
    fun `limiter factory is bound`() {
      assertThat(limit).isInstanceOf(SettableLimit::class.java)
      assertThat(limiterFactories).hasSize(1)
      assertThat(limiterFactories.createLimit()).isInstanceOf(SimpleLimiter::class.java)
    }
  }

  @Nested
  inner class FixedStrategyTest {
    init {
      strategy = ConcurrencyLimiterStrategy.FIXED
      maxConcurrency = 3
    }

    @Test
    fun `limiter factory is bound`() {
      assertThat(limit).isInstanceOf(FixedLimit::class.java)
      assertThat(limiterFactories).hasSize(1)
      assertThat(limiterFactories.createLimit()).isInstanceOf(SimpleLimiter::class.java)
    }
  }
}
