package misk.ratelimiting.bucket4j.dynamodb.v1

import com.google.inject.Module
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.aws.dynamodb.testing.DockerDynamoDbModule
import misk.aws.dynamodb.testing.DynamoDbTable
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.deployment.TESTING
import wisp.ratelimiting.RateLimiter
import wisp.ratelimiting.testing.TestRateLimitConfig

@MiskTest(startService = true)
class DynamoDBV1RateLimiterTest {
  @Suppress("unused")
  @MiskTestModule
  private val module: Module =
    object : KAbstractModule() {
      override fun configure() {
        install(DockerDynamoDbModule(DynamoDbTable(DyRateLimitBucket::class)))
        install(DynamoDbV1Bucket4jRateLimiterModule("rate_limit_buckets"))
        install(MiskTestingServiceModule())
        install(DeploymentModule(TESTING))
        bind<MeterRegistry>().toInstance(SimpleMeterRegistry())
      }
    }

  @Inject private lateinit var fakeClock: FakeClock

  @Inject private lateinit var rateLimiter: RateLimiter

  @Test
  fun `can take tokens up to limit`() {
    repeat(TestRateLimitConfig.capacity.toInt()) {
      val result = rateLimiter.consumeToken(KEY, TestRateLimitConfig)
      with(result) {
        assertThat(didConsume).isTrue()
        assertThat(remaining).isEqualTo(rateLimiter.availableTokens(KEY, TestRateLimitConfig)).isEqualTo(4L - it)
      }
    }
    val result = rateLimiter.consumeToken(KEY, TestRateLimitConfig)
    with(result) {
      assertThat(didConsume).isFalse()
      assertThat(remaining).isEqualTo(rateLimiter.availableTokens(KEY, TestRateLimitConfig)).isZero()
    }
  }

  @Test
  fun `withToken respects limits`() {
    var counter = 0
    repeat((TestRateLimitConfig.capacity * 2).toInt()) { rateLimiter.withToken(KEY, TestRateLimitConfig) { counter++ } }
    // We should have been able to increment the counter until we consumed the bucket
    assertThat(counter).isEqualTo(TestRateLimitConfig.capacity)
  }

  @Test
  fun `bucket is refilled on schedule`() {
    var counter = 0
    repeat(TestRateLimitConfig.capacity.toInt()) { rateLimiter.withToken(KEY, TestRateLimitConfig) { counter++ } }

    val result = rateLimiter.withToken(KEY, TestRateLimitConfig) { counter++ }
    assertThat(result.consumptionData.didConsume).isFalse()
    assertThat(result.result).isNull()
    assertThat(result.consumptionData.remaining)
      .isEqualTo(rateLimiter.availableTokens(KEY, TestRateLimitConfig))
      .isZero()
    assertThat(counter).isEqualTo(TestRateLimitConfig.capacity)

    // Elapse enough time that the next request refills the bucket
    fakeClock.add(TestRateLimitConfig.refillPeriod)
    repeat(TestRateLimitConfig.capacity.toInt()) { rateLimiter.withToken(KEY, TestRateLimitConfig) { counter++ } }
    assertThat(counter).isEqualTo(10)
  }

  companion object {
    private const val KEY = "test_key"
  }
}
