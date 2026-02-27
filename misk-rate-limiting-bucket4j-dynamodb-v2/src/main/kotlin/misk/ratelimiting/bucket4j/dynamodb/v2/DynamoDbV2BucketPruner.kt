package misk.ratelimiting.bucket4j.dynamodb.v2

import io.micrometer.core.instrument.MeterRegistry
import java.time.Clock
import java.time.Duration
import kotlin.collections.forEach
import kotlin.system.measureTimeMillis
import misk.logging.getLogger
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ComparisonOperator
import software.amazon.awssdk.services.dynamodb.model.ExpectedAttributeValue
import software.amazon.awssdk.services.dynamodb.model.ReturnValue
import wisp.ratelimiting.RateLimitPrunerMetrics
import wisp.ratelimiting.bucket4j.Bucket4jPruner
import wisp.ratelimiting.bucket4j.ClockTimeMeter

class DynamoDbV2BucketPruner
@JvmOverloads
constructor(
  clock: Clock,
  private val dynamoDb: DynamoDbClient,
  meterRegistry: MeterRegistry,
  private val tableName: String,
  private val pageSize: Int = 1000,
  private val retryTimeout: Duration = Duration.ofMillis(25),
) : Bucket4jPruner() {
  override val clockTimeMeter = ClockTimeMeter(clock)

  private val prunerMetrics = RateLimitPrunerMetrics(meterRegistry)

  override fun prune() {
    val millisTaken = measureTimeMillis { pruneLoop() }
    prunerMetrics.pruningDuration.record(millisTaken.toDouble())
  }

  private fun pruneLoop() {
    val pager =
      dynamoDb.scanPaginator { request ->
        request.overrideConfiguration { config -> config.apiCallTimeout(retryTimeout) }
        request.tableName(tableName)
        request.limit(pageSize)
        request.consistentRead(true)
      }

    pager.items().forEach { item ->
      val stateBytes =
        item[STATE_ATTRIBUTE]?.b()?.asByteArray()
          ?: run {
            logger.warn {
              "Row ${item[HASH_ATTRIBUTE]?.s()} is missing its state attribute, " + "this should never happen. Skipping"
            }
            return@forEach
          }
      val state = deserializeState(stateBytes)
      if (isBucketStale(state)) {
        val id =
          item[HASH_ATTRIBUTE]?.s()
            ?: run {
              logger.warn { "Row is missing its id attribute, this should never happen. Skipping" }
              return@forEach
            }
        val expectedValue =
          ExpectedAttributeValue.builder()
            .comparisonOperator(ComparisonOperator.EQ)
            .value(AttributeValue.fromB(SdkBytes.fromByteArray(stateBytes)))
            .build()
        val deletionResult =
          dynamoDb.deleteItem { request ->
            request.overrideConfiguration { config -> config.apiCallTimeout(retryTimeout) }
            request.tableName(tableName)
            request.key(mapOf(HASH_ATTRIBUTE to AttributeValue.fromS(id)))
            request.expected(mapOf(STATE_ATTRIBUTE to expectedValue))
            request.returnValues(ReturnValue.ALL_OLD)
          }

        if (deletionResult.attributes().isNotEmpty()) {
          prunerMetrics.bucketsPruned.increment()
        }
      }
    }
  }

  companion object {
    private val logger = getLogger<DynamoDbV2BucketPruner>()

    // These values are hardcoded in bucket4j
    private const val HASH_ATTRIBUTE = "key"
    private const val STATE_ATTRIBUTE = "state"
  }
}
