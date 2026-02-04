package misk.ratelimiting.bucket4j.dynamodb.v2.transaction

import io.github.bucket4j.distributed.proxy.generic.compare_and_swap.CompareAndSwapOperation
import io.github.bucket4j.distributed.remote.RemoteBucketState
import java.util.Optional
import kotlin.collections.getValue
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException

internal abstract class BaseDynamoDBTransaction(private val dynamoDB: DynamoDbClient, private val table: String) :
  CompareAndSwapOperation {
  override fun getStateData(timeoutNanos: Optional<Long>): Optional<ByteArray> {
    val attributes = mapOf(DEFAULT_KEY_NAME to getKeyAttributeValue())

    // TODO: respect timeout
    val result =
      dynamoDB
        .getItem {
          it.tableName(table)
          it.key(attributes)
          it.consistentRead(true)
        }
        .item()
    if (result == null || !result.containsKey(DEFAULT_STATE_NAME)) {
      return Optional.empty()
    }

    val state = result.getValue(DEFAULT_STATE_NAME)
    check(state.b() != null) {
      "state (attribute: $DEFAULT_STATE_NAME) value is corrupted for key " +
        "${getKeyAttributeValue()}. It is present but value type is different from " +
        "Binary (B) type. Current state value is $state"
    }

    return Optional.of(state.b().asByteArray())
  }

  override fun compareAndSwap(originalData: ByteArray?, newData: ByteArray, newState: RemoteBucketState?, timeoutNanos: Optional<Long>): Boolean {
    val item =
      mapOf(
        DEFAULT_KEY_NAME to getKeyAttributeValue(),
        DEFAULT_STATE_NAME to AttributeValue.fromB(newData.toSdkBytes()),
      )
    val attributes =
      mapOf(":expected" to AttributeValue.fromB(originalData?.toSdkBytes() ?: SdkBytes.fromUtf8String("")))
    val names = mapOf("#st" to DEFAULT_STATE_NAME)

    // TODO: respect timeouts
    return try {
      dynamoDB.putItem {
        it.tableName(table)
        it.item(item)
        it.conditionExpression("attribute_not_exists(#st) OR #st = :expected")
        it.expressionAttributeNames(names)
        it.expressionAttributeValues(attributes)
      }
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

  private fun ByteArray.toSdkBytes() = SdkBytes.fromByteArray(this)
}
