package misk.ratelimiting.bucket4j.mysql

import com.google.inject.Injector
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import jakarta.inject.Inject
import java.time.Duration
import misk.MiskTestingServiceModule
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.jdbc.DataSourceService
import misk.jdbc.JdbcModule
import misk.jdbc.JdbcTestingModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.deployment.TESTING
import wisp.ratelimiting.RateLimitConfiguration
import wisp.ratelimiting.RateLimitPruner
import wisp.ratelimiting.RateLimitPrunerMetrics
import wisp.ratelimiting.RateLimiter
import wisp.ratelimiting.testing.TestRateLimitConfig

@MiskTest(startService = true)
class MySQLBucketPrunerTests {
  @Suppress("unused")
  @MiskTestModule
  val module =
    object : KAbstractModule() {
      override fun configure() {
        install(MiskTestingServiceModule())
        install(DeploymentModule(TESTING))
        val config = MiskConfig.load<MySQLBucket4jRateLimiterTests.RootConfig>("mysql_rate_limiter_test", TESTING)
        install(JdbcTestingModule(RateLimits::class))
        install(JdbcModule(RateLimits::class, config.mysql_data_source))

        install(
          MySQLBucket4jRateLimiterModule(
            qualifier = RateLimits::class,
            tableName = MySQLBucket4jRateLimiterTests.TABLE_NAME,
            idColumn = MySQLBucket4jRateLimiterTests.ID_COLUMN,
            stateColumn = MySQLBucket4jRateLimiterTests.STATE_COLUMN,
            prunerPageSize = 2L,
          )
        )
        bind<MeterRegistry>().toInstance(SimpleMeterRegistry())
      }
    }

  @Inject private lateinit var fakeClock: FakeClock

  @Inject private lateinit var injector: Injector

  @Inject private lateinit var meterRegistry: MeterRegistry

  @Inject private lateinit var pruner: RateLimitPruner

  @Inject private lateinit var rateLimiter: RateLimiter

  private val getAllKeysQuery =
    """
      SELECT ${MySQLBucket4jRateLimiterTests.ID_COLUMN} 
      FROM ${MySQLBucket4jRateLimiterTests.TABLE_NAME}
    """
      .trimIndent()

  private val prunerMetrics by lazy { RateLimitPrunerMetrics(meterRegistry) }

  @Test
  fun `pruning deletes only expired rows`() {
    // Create group of expired keys
    val expiredBucketKeys = buildList { repeat(10) { add("expired-$it") } }
    expiredBucketKeys.forEach { key -> rateLimiter.consumeToken(key, TestRateLimitConfig) }

    val mixedBucketKeys = buildList { repeat(10) { add("maybeexpired-$it") } }

    // Create group of interleaved short and long buckets
    mixedBucketKeys.forEachIndexed { idx, key ->
      if (idx % 2 == 0) {
        rateLimiter.consumeToken(key, LongTestRateLimitConfig)
      } else {
        rateLimiter.consumeToken(key, TestRateLimitConfig)
      }
    }

    fakeClock.add(TestRateLimitConfig.refillPeriod)

    // Create group of non-expired keys
    val nonExpiredShortBucketKeys = buildList { repeat(10) { add("nonexpired-short-$it") } }
    nonExpiredShortBucketKeys.forEach { key -> rateLimiter.consumeToken(key, TestRateLimitConfig) }

    pruner.prune()

    assertThat(prunerMetrics.bucketsPruned.count()).isEqualTo(15.0)
    // Use count to verify interaction instead of checking the duration
    //  sometimes in tests the time can be so fast it registers as 0, resulting in a flaky test
    assertThat(prunerMetrics.pruningDuration.count()).isGreaterThan(0L)

    val partitionedBucketKeys = mixedBucketKeys.withIndex().partition { it.index % 2 == 0 }
    val nonExpiredLongKeys = partitionedBucketKeys.first.map { it.value }

    val dataSource = injector.getInstance(keyOf<DataSourceService>(RateLimits::class)).dataSource
    val connection = dataSource.connection
    val statement = connection.createStatement()
    val resultSet = statement.executeQuery(getAllKeysQuery)
    val extantKeys = buildList {
      while (resultSet.next()) {
        add(resultSet.getString(MySQLBucket4jRateLimiterTests.ID_COLUMN))
      }
    }

    // We should have deleted only the short duration keys that were created before advancing time,
    // leaving the long duration keys and the short duration keys created after advancing time
    assertThat(extantKeys).containsExactlyElementsOf(nonExpiredLongKeys + nonExpiredShortBucketKeys)
  }

  private object LongTestRateLimitConfig : RateLimitConfiguration {
    private const val BUCKET_CAPACITY = 5L
    private val REFILL_DURATION: Duration = Duration.ofHours(1L)

    override val capacity = BUCKET_CAPACITY
    override val name = "long_test_configuration"
    override val refillAmount = BUCKET_CAPACITY
    override val refillPeriod: Duration = REFILL_DURATION
  }
}
