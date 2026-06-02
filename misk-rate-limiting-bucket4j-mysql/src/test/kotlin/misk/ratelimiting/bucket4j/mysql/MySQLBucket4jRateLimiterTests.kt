package misk.ratelimiting.bucket4j.mysql

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import jakarta.inject.Inject
import jakarta.inject.Qualifier
import misk.MiskTestingServiceModule
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceConfig
import misk.jdbc.JdbcModule
import misk.jdbc.JdbcTestingModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.deployment.TESTING
import wisp.ratelimiting.RateLimiter
import wisp.ratelimiting.testing.TestRateLimitConfig

@MiskTest(startService = true)
class MySQLBucket4jRateLimiterTests {
  @Suppress("unused")
  @MiskTestModule
  val module =
    object : KAbstractModule() {
      override fun configure() {
        install(MiskTestingServiceModule())
        install(DeploymentModule(TESTING))
        val config = MiskConfig.load<RootConfig>("mysql_rate_limiter_test", TESTING)
        install(JdbcTestingModule(RateLimits::class))
        install(JdbcModule(RateLimits::class, config.mysql_data_source))

        install(MySQLBucket4jRateLimiterModule(RateLimits::class, TABLE_NAME, ID_COLUMN, STATE_COLUMN))
        bind<MeterRegistry>().toInstance(SimpleMeterRegistry())
      }
    }

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

  internal data class RootConfig(val mysql_data_source: DataSourceConfig) : Config

  companion object {
    internal const val KEY = "test_key"
    internal const val TABLE_NAME = "rate_limit_buckets"
    internal const val ID_COLUMN = "id"
    internal const val STATE_COLUMN = "state"
  }
}

@Qualifier @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION) internal annotation class RateLimits
