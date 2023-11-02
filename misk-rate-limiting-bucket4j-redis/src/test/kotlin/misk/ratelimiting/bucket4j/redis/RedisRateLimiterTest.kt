package misk.ratelimiting.bucket4j.redis

import com.google.inject.Module
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.redis.RedisModule
import misk.redis.testing.DockerRedis
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import redis.clients.jedis.JedisPoolConfig
import wisp.deployment.TESTING
import wisp.ratelimiting.RateLimiter
import wisp.ratelimiting.testing.TestRateLimitConfig

@MiskTest
class RedisRateLimiterTest {
  @Suppress("unused")
  @MiskTestModule
  private val module: Module = object: KAbstractModule() {
    override fun configure() {
      install(RedisModule(DockerRedis.config, JedisPoolConfig(), useSsl = false))
      install(RedisBucket4jRateLimiterModule())
      install(MiskTestingServiceModule())
      install(DeploymentModule(TESTING))
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
        Assertions.assertThat(didConsume).isTrue()
        Assertions.assertThat(remaining).isEqualTo(4L - it)
      }
    }
    val result = rateLimiter.consumeToken(KEY, TestRateLimitConfig)
    with(result) {
      Assertions.assertThat(didConsume).isFalse()
      Assertions.assertThat(remaining).isZero()
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
    Assertions.assertThat(counter).isEqualTo(TestRateLimitConfig.capacity)
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
    Assertions.assertThat(result.consumptionData.didConsume).isFalse()
    Assertions.assertThat(result.result).isNull()
    Assertions.assertThat(result.consumptionData.remaining).isZero()
    Assertions.assertThat(counter).isEqualTo(TestRateLimitConfig.capacity)

    // Elapse enough time that the next request refills the bucket
    fakeClock.add(TestRateLimitConfig.refillPeriod)
    repeat(TestRateLimitConfig.capacity.toInt()) {
      rateLimiter.withToken(KEY, TestRateLimitConfig) {
        counter++
      }
    }
    Assertions.assertThat(counter).isEqualTo(10)
  }

  companion object {
    private const val KEY = "test_key"
  }
}
