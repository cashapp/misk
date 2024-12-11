package misk.ratelimiting.bucket4j.redis

import com.google.inject.Module
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.redis.RedisModule
import misk.redis.testing.DockerRedis
import misk.redis.testing.RedisTestFlushModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import redis.clients.jedis.ConnectionPoolConfig
import wisp.deployment.TESTING
import wisp.ratelimiting.RateLimiter
import wisp.ratelimiting.testing.TestRateLimitConfig
import wisp.ratelimiting.testing.TestRateLimitConfigRefillGreedily

@MiskTest(startService = true)
class RedisRateLimiterTest {
  @Suppress("unused")
  @MiskTestModule
  private val module: Module = object : KAbstractModule() {
    override fun configure() {
      install(RedisModule(DockerRedis.replicationGroupConfig, ConnectionPoolConfig(), useSsl = false))
      install(RedisBucket4jRateLimiterModule())
      install(MiskTestingServiceModule())
      install(RedisTestFlushModule())
      install(DeploymentModule(TESTING))
      bind<MeterRegistry>().toInstance(SimpleMeterRegistry())
    }
  }

  @Inject private lateinit var rateLimiter: RateLimiter

  @Inject private lateinit var fakeClock: FakeClock

  @Test
  fun `can take tokens up to limit`() {
    repeat(TestRateLimitConfig.capacity.toInt()) {
      val result = rateLimiter.consumeToken(KEY, TestRateLimitConfig)
      with(result) {
        assertThat(didConsume).isTrue()
        assertThat(remaining)
          .isEqualTo(rateLimiter.availableTokens(KEY, TestRateLimitConfig))
          .isEqualTo(4L - it)
      }
    }
    val result = rateLimiter.consumeToken(KEY, TestRateLimitConfig)
    with(result) {
      assertThat(didConsume).isFalse()
      assertThat(remaining)
        .isEqualTo(rateLimiter.availableTokens(KEY, TestRateLimitConfig))
        .isZero()
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
    assertThat(result.consumptionData.remaining)
      .isEqualTo(rateLimiter.availableTokens(KEY, TestRateLimitConfig))
      .isZero()
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

  @Test
  fun `test bucket refilled at the end of the interval after consuming all tokens`() {
    val increment = TestRateLimitConfig.refillPeriod.dividedBy(5)
    repeat(5) {
      val result = rateLimiter.consumeToken(KEY, TestRateLimitConfig)
      assertThat(result.didConsume).isTrue()
      assertThat(result.remaining).isEqualTo(TestRateLimitConfig.capacity - 1 - it)
      fakeClock.add(increment)
    }

    assertThat(rateLimiter.availableTokens(KEY, TestRateLimitConfig)).isEqualTo(5L)
  }

  @Test
  fun `test bucket refilled at the end of the interval after consuming some tokens`() {
    val increment = TestRateLimitConfig.refillPeriod.dividedBy(5)
    repeat(3) {
      val result = rateLimiter.consumeToken(KEY, TestRateLimitConfig)
      assertThat(result.didConsume).isTrue()
      assertThat(result.remaining).isEqualTo(TestRateLimitConfig.capacity - 1 - it)
      fakeClock.add(increment)
    }

    assertThat(rateLimiter.availableTokens(KEY, TestRateLimitConfig)).isEqualTo(2L)
    fakeClock.add(increment)
    assertThat(rateLimiter.availableTokens(KEY, TestRateLimitConfig)).isEqualTo(2L)
    fakeClock.add(increment) // the clock now has past the end of the interval
    assertThat(rateLimiter.availableTokens(KEY, TestRateLimitConfig)).isEqualTo(5L)
  }

  @Test
  fun `test bucket refilled continuously after each increment`() {
    repeat(5) {
      val result = rateLimiter.consumeToken(KEY, TestRateLimitConfigRefillGreedily)
      assertThat(result.didConsume).isTrue()
      assertThat(result.remaining).isEqualTo(TestRateLimitConfigRefillGreedily.capacity - 1 - it)
    }
    assertThat(rateLimiter.availableTokens(KEY, TestRateLimitConfigRefillGreedily)).isEqualTo(0L)
    assertThat(rateLimiter.consumeToken(KEY, TestRateLimitConfigRefillGreedily).didConsume).isFalse()

    val increment = TestRateLimitConfigRefillGreedily.refillPeriod.dividedBy(5)
    repeat(5) {
      // One token is added back after each increment
      fakeClock.add(increment)
      assertThat(rateLimiter.availableTokens(KEY, TestRateLimitConfigRefillGreedily)).isEqualTo(it + 1L)
    }
  }

  @Test
  fun `test bucket refilled continuously`() {
    val increment = TestRateLimitConfigRefillGreedily.refillPeriod.dividedBy(5)
    repeat(5) {
      val result = rateLimiter.consumeToken(KEY, TestRateLimitConfigRefillGreedily)
      assertThat(result.didConsume).isTrue()
      assertThat(result.remaining).isEqualTo(TestRateLimitConfigRefillGreedily.capacity - 1)

      assertThat(rateLimiter.availableTokens(KEY, TestRateLimitConfigRefillGreedily)).isEqualTo(4L)
      fakeClock.add(increment)
      assertThat(rateLimiter.availableTokens(KEY, TestRateLimitConfigRefillGreedily)).isEqualTo(5L)
    }

    assertThat(rateLimiter.availableTokens(KEY, TestRateLimitConfigRefillGreedily)).isEqualTo(5L)
  }

  companion object {
    private const val KEY = "test_key"
  }
}
