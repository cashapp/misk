package misk.ratelimiting.bucket4j.dynamodb.v2

import com.google.inject.Module
import io.micrometer.core.instrument.MeterRegistry
import jakarta.inject.Inject
import misk.ratelimiting.bucket4j.dynamodb.v2.modules.DynamoDbStringTestModule
import misk.ratelimiting.bucket4j.dynamodb.v2.modules.DynamoDbStringTestModule.Companion.STRING_TABLE_NAME
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import wisp.ratelimiting.RateLimitConfiguration
import wisp.ratelimiting.RateLimitPruner
import wisp.ratelimiting.RateLimitPrunerMetrics
import wisp.ratelimiting.RateLimiter
import wisp.ratelimiting.testing.TestRateLimitConfig
import java.time.Duration

@MiskTest(startService = true)
class DynamoDBV2PrunerTest {
  @Suppress("unused")
  @MiskTestModule
  private val module: Module = DynamoDbStringTestModule()

  @Inject private lateinit var dynamoDb: DynamoDbClient

  @Inject private lateinit var fakeClock: FakeClock

  @Inject private lateinit var meterRegistry: MeterRegistry

  @Inject private lateinit var rateLimiter: RateLimiter

  @Inject private lateinit var pruner: RateLimitPruner

  private val prunerMetrics by lazy {
    RateLimitPrunerMetrics(meterRegistry)
  }

  @Test
  fun `pruning deletes only expired rows`() {
    // Create group of expired keys
    val expiredBucketKeys = buildList {
      repeat(10) {
        add("expired-$it")
      }
    }
    expiredBucketKeys.forEach { key ->
      rateLimiter.consumeToken(key, TestRateLimitConfig)
    }

    val mixedBucketKeys = buildList {
      repeat(10) {
        add("maybeexpired-$it")
      }
    }

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
    val nonExpiredShortBucketKeys = buildList {
      repeat(10) {
        add("nonexpired-short-$it")
      }
    }
    nonExpiredShortBucketKeys.forEach { key ->
      rateLimiter.consumeToken(key, TestRateLimitConfig)
    }

    pruner.prune()

    Assertions.assertThat(prunerMetrics.bucketsPruned.count()).isEqualTo(15.0)
    // Use count to verify interaction instead of checking the duration
    //  sometimes in tests the time can be so fast it registers as 0, resulting in a flaky test
    Assertions.assertThat(prunerMetrics.pruningDuration.count()).isGreaterThan(0L)

    val partitionedBucketKeys = mixedBucketKeys.withIndex().partition {
      it.index % 2 == 0
    }
    val nonExpiredLongKeys = partitionedBucketKeys.first.map { it.value }

    val extantKeys = getAllKeysInTable()

    // We should have deleted only the short duration keys that were created before advancing time,
    // leaving the long duration keys and the short duration keys created after advancing time
    Assertions.assertThat(extantKeys).containsExactlyInAnyOrderElementsOf(
      nonExpiredLongKeys + nonExpiredShortBucketKeys
    )
  }

  private fun getAllKeysInTable(): List<String> {
    val pager = dynamoDb.scanPaginator {
      it.tableName(STRING_TABLE_NAME)
      it.consistentRead(true)
    }

    return pager.items().map { item ->
      item["key"]!!.s()
    }
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
