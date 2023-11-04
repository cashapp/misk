package misk.ratelimiting.bucket4j.mysql

import com.google.inject.Provides
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
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
  @MiskTestModule val module = object : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(DeploymentModule(TESTING))
      val config = MiskConfig.load<RootConfig>("mysql_rate_limiter_test", TESTING)
      install(JdbcTestingModule(RateLimits::class))
      install(JdbcModule(RateLimits::class, config.mysql_data_source))

      install(
        MySQLBucket4jRateLimiterModule(RateLimits::class, TABLE_NAME, ID_COLUMN, STATE_COLUMN)
      )
    }

    @Provides @Singleton
    // In prod this is provided by Skim
    fun provideMeterRegistry(collectorRegistry: CollectorRegistry): MeterRegistry {
      return PrometheusMeterRegistry(
        PrometheusConfig.DEFAULT, collectorRegistry, Clock.SYSTEM
      )
    }
  }

  @Inject private lateinit var rateLimiter: RateLimiter

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

  private data class RootConfig(
    val mysql_data_source: DataSourceConfig
  ) : Config

  companion object {
    private const val KEY = "test_key"
    private const val TABLE_NAME = "rate_limit_buckets"
    private const val ID_COLUMN = "id"
    private const val STATE_COLUMN = "state"
  }
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
internal annotation class RateLimits
