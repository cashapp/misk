package misk.ratelimiting.bucket4j.redis

import com.google.inject.Module
import com.google.inject.Provides
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.redis.testing.DockerRedis
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import redis.clients.jedis.JedisPoolConfig
import wisp.deployment.TESTING
import wisp.ratelimiting.RateLimiter
import wisp.ratelimiting.testing.TestRateLimitConfig

@MiskTest(startService = true)
class RedisRateLimiterTest {
  @Suppress("unused")
  @MiskTestModule
  private val module: Module = object : KAbstractModule() {
    override fun configure() {
      install(RedisBucket4jRateLimiterModule(DockerRedis.config, JedisPoolConfig(), useSsl = false))
      install(MiskTestingServiceModule())
      install(DeploymentModule(TESTING))
    }

    @Provides @Singleton
    // In prod this is provided by Skim
    fun provideMeterRegistry(collectorRegistry: CollectorRegistry): MeterRegistry {
      return PrometheusMeterRegistry(
        PrometheusConfig.DEFAULT, collectorRegistry, Clock.SYSTEM
      )
    }
  }

  @Suppress("unused")
  @MiskExternalDependency
  private val dockerRedis = DockerRedis

  @Inject private lateinit var rateLimiter: RateLimiter

  @Inject private lateinit var fakeClock: FakeClock

  @Test
  fun `can take tokens up to limit`() {
    repeat(TestRateLimitConfig.capacity.toInt()) {
      val result = rateLimiter.consumeToken(KEY, TestRateLimitConfig)
      with(result) {
        assertThat(didConsume).isTrue()
        assertThat(remaining).isEqualTo(4L - it)
      }
    }
    val result = rateLimiter.consumeToken(KEY, TestRateLimitConfig)
    with(result) {
      assertThat(didConsume).isFalse()
      assertThat(remaining).isZero()
    }
  }

  @Test
  fun `withToken respects limits`() {
    var counter = 0
    repeat((TestRateLimitConfig.capacity * 2).toInt()) {
      rateLimiter.withToken(KEY, TestRateLimitConfig) {
        counter++
      }
    }
    // We should have been able to increment the counter until we consumed the bucket
    assertThat(counter).isEqualTo(TestRateLimitConfig.capacity)
  }

  @Test
  fun `bucket is refilled on schedule`() {
    var counter = 0
    repeat(TestRateLimitConfig.capacity.toInt()) {
      rateLimiter.withToken(KEY, TestRateLimitConfig) {
        counter++
      }
    }

    val result = rateLimiter.withToken(KEY, TestRateLimitConfig) {
      counter++
    }
    assertThat(result.consumptionData.didConsume).isFalse()
    assertThat(result.result).isNull()
    assertThat(result.consumptionData.remaining).isZero()
    assertThat(counter).isEqualTo(TestRateLimitConfig.capacity)

    // Elapse enough time that the next request refills the bucket
    fakeClock.add(TestRateLimitConfig.refillPeriod)
    repeat(TestRateLimitConfig.capacity.toInt()) {
      rateLimiter.withToken(KEY, TestRateLimitConfig) {
        counter++
      }
    }
    assertThat(counter).isEqualTo(10)
  }

  companion object {
    private const val KEY = "test_key"
  }
}
