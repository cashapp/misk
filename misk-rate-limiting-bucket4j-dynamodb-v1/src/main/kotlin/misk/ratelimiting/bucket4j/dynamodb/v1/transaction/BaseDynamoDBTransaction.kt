package misk.ratelimiting.bucket4j.dynamodb.v1.transaction

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.document.ItemUtils
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException
import com.amazonaws.services.dynamodbv2.model.GetItemRequest
import com.amazonaws.services.dynamodbv2.model.PutItemRequest
import io.github.bucket4j.distributed.proxy.generic.compare_and_swap.CompareAndSwapOperation
import io.github.bucket4j.distributed.remote.RemoteBucketState
import java.nio.ByteBuffer
import java.util.Optional
import java.util.concurrent.TimeUnit

internal abstract class BaseDynamoDBTransaction(private val dynamoDB: AmazonDynamoDB, private val table: String) :
  CompareAndSwapOperation {
  override fun getStateData(timeoutNanos: Optional<Long>): Optional<ByteArray> {
    val attributes = mapOf(DEFAULT_KEY_NAME to getKeyAttributeValue())

    val result =
      dynamoDB
        .getItem(
          GetItemRequest().withTableName(table).withKey(attributes).withConsistentRead(true).apply {
            timeoutNanos.ifPresent { timeout ->
              withSdkRequestTimeout<PutItemRequest>(TimeUnit.NANOSECONDS.toMillis(timeout).toInt())
            }
          }
        )
        .item
    if (result == null || !result.containsKey(DEFAULT_STATE_NAME)) {
      return Optional.empty()
    }

    val state = result.getValue(DEFAULT_STATE_NAME)
    check(state.b != null) {
      "state (attribute: $DEFAULT_STATE_NAME) value is corrupted for key " +
        "${getKeyAttributeValue()}. It is present but value type is different from " +
        "Binary (B) type. Current state value is $state"
    }

    return Optional.of(state.b.array())
  }

  override fun compareAndSwap(
    originalData: ByteArray?,
    newData: ByteArray,
    newState: RemoteBucketState?,
    timeoutNanos: Optional<Long>,
  ): Boolean {
    val item =
      mapOf(
        DEFAULT_KEY_NAME to getKeyAttributeValue(),
        DEFAULT_STATE_NAME to AttributeValue().withB(ByteBuffer.wrap(newData)),
      )
    val expectedValue = originalData ?: ByteArray(0)
    val attributes = mapOf(":expected" to expectedValue)
    val names = mapOf("#st" to DEFAULT_STATE_NAME)

    return try {
      dynamoDB.putItem(
        PutItemRequest()
          .withTableName(table)
          .withItem(item)
          .withConditionExpression("attribute_not_exists(#st) OR #st = :expected")
          .withExpressionAttributeNames(names)
          .withExpressionAttributeValues(ItemUtils.fromSimpleMap(attributes))
          .apply {
            timeoutNanos.ifPresent { timeout ->
              withSdkRequestTimeout<PutItemRequest>(TimeUnit.NANOSECONDS.toMillis(timeout).toInt())
            }
          }
      )
      true
    } catch (_: ConditionalCheckFailedException) {
      false
    }
  }

  /** @return The key wrapped in an [AttributeValue]. The type of the key depends on the implementation */
  protected abstract fun getKeyAttributeValue(): AttributeValue

  companion object {
    internal const val DEFAULT_KEY_NAME = "key"
    internal const val DEFAULT_STATE_NAME = "state"
  }
}
