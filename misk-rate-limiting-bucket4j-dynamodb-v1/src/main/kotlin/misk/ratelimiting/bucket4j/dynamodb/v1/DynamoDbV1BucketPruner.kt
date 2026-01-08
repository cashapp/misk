package misk.ratelimiting.bucket4j.dynamodb.v1

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.document.Expected
import com.amazonaws.services.dynamodbv2.document.spec.DeleteItemSpec
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec
import com.amazonaws.services.dynamodbv2.model.ReturnValue
import io.micrometer.core.instrument.MeterRegistry
import java.time.Clock
import kotlin.system.measureTimeMillis
import misk.logging.getLogger
import wisp.ratelimiting.RateLimitPrunerMetrics
import wisp.ratelimiting.bucket4j.Bucket4jPruner
import wisp.ratelimiting.bucket4j.ClockTimeMeter

class DynamoDbV1BucketPruner
@JvmOverloads
constructor(
  clock: Clock,
  amazonDynamoDB: AmazonDynamoDB,
  meterRegistry: MeterRegistry,
  private val tableName: String,
  private val pageSize: Int = 1000,
) : Bucket4jPruner() {
  override val clockTimeMeter = ClockTimeMeter(clock)

  private val dynamoDB = DynamoDB(amazonDynamoDB)
  private val prunerMetrics = RateLimitPrunerMetrics(meterRegistry)

  override fun prune() {
    val millisTaken = measureTimeMillis { pruneLoop() }
    prunerMetrics.pruningDuration.record(millisTaken.toDouble())
  }

  private fun pruneLoop() {
    val table = dynamoDB.getTable(tableName)
    val scanSpec = ScanSpec().withMaxPageSize(pageSize).withConsistentRead(true)

    val result = table.scan(scanSpec)
    result.pages().forEach { page ->
      page.iterator().forEach inner@{ item ->
        val stateBytes =
          item.getByteBuffer(STATE_ATTRIBUTE)
            ?: run {
              logger.warn {
                "Row ${item.getString(HASH_ATTRIBUTE)} is missing its state attribute, " +
                  "this should never happen. Skipping"
              }
              return@inner
            }
        val state = deserializeState(stateBytes.array())
        if (isBucketStale(state)) {
          val id =
            item.getString(HASH_ATTRIBUTE)
              ?: run {
                logger.warn { "Row is missing its id attribute, this should never happen. Skipping" }
                return@inner
              }
          val deleteRequest =
            DeleteItemSpec()
              .withPrimaryKey(HASH_ATTRIBUTE, id)
              .withExpected(Expected(STATE_ATTRIBUTE).eq(stateBytes))
              .withReturnValues(ReturnValue.ALL_OLD)
          val deletionResult = table.deleteItem(deleteRequest)

          if (deletionResult.deleteItemResult.attributes.isNotEmpty()) {
            prunerMetrics.bucketsPruned.increment()
          }
        }
      }
    }
  }

  companion object {
    private val logger = getLogger<DynamoDbV1BucketPruner>()
    // These values are hardcoded in bucket4j
    private const val HASH_ATTRIBUTE = "key"
    private const val STATE_ATTRIBUTE = "state"
  }
}
