package misk.ratelimiting.bucket4j.dynamodb.v1

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec
import com.google.inject.Module
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import jakarta.inject.Inject
import java.time.Duration
import misk.MiskTestingServiceModule
import misk.aws.dynamodb.testing.DockerDynamoDbModule
import misk.aws.dynamodb.testing.DynamoDbTable
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import wisp.deployment.TESTING
import wisp.ratelimiting.RateLimitConfiguration
import wisp.ratelimiting.RateLimitPruner
import wisp.ratelimiting.RateLimitPrunerMetrics
import wisp.ratelimiting.RateLimiter
import wisp.ratelimiting.testing.TestRateLimitConfig

@MiskTest(startService = true)
class DynamoDBV1PrunerTest {
  @Suppress("unused")
  @MiskTestModule
  private val module: Module =
    object : KAbstractModule() {
      override fun configure() {
        install(DockerDynamoDbModule(DynamoDbTable(DyRateLimitBucket::class)))
        install(DynamoDbV1Bucket4jRateLimiterModule(TABLE_NAME, prunerPageSize = 2))
        install(MiskTestingServiceModule())
        install(DeploymentModule(TESTING))
        bind<MeterRegistry>().toInstance(SimpleMeterRegistry())
      }
    }

  @Inject private lateinit var amazonDynamoDB: AmazonDynamoDB

  @Inject private lateinit var fakeClock: FakeClock

  @Inject private lateinit var meterRegistry: MeterRegistry

  @Inject private lateinit var rateLimiter: RateLimiter

  @Inject private lateinit var pruner: RateLimitPruner

  private val dynamoDB by lazy { DynamoDB(amazonDynamoDB) }

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

    Assertions.assertThat(prunerMetrics.bucketsPruned.count()).isEqualTo(15.0)
    // Use count to verify interaction instead of checking the duration
    //  sometimes in tests the time can be so fast it registers as 0, resulting in a flaky test
    Assertions.assertThat(prunerMetrics.pruningDuration.count()).isGreaterThan(0L)

    val partitionedBucketKeys = mixedBucketKeys.withIndex().partition { it.index % 2 == 0 }
    val nonExpiredLongKeys = partitionedBucketKeys.first.map { it.value }

    val extantKeys = getAllKeysInTable()

    // We should have deleted only the short duration keys that were created before advancing time,
    // leaving the long duration keys and the short duration keys created after advancing time
    Assertions.assertThat(extantKeys)
      .containsExactlyInAnyOrderElementsOf(nonExpiredLongKeys + nonExpiredShortBucketKeys)
  }

  private fun getAllKeysInTable(): List<String> {
    val table = dynamoDB.getTable(TABLE_NAME)
    val scanSpec = ScanSpec().withConsistentRead(true)

    val result = table.scan(scanSpec)
    return buildList {
      result.pages().forEach { page -> page.iterator().forEach { item -> add(item.getString("key")) } }
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

  companion object {
    private const val TABLE_NAME = "rate_limit_buckets"
  }
}
