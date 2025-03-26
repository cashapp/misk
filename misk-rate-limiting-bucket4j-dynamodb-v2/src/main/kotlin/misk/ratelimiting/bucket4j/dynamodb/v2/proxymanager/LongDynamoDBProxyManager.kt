package misk.ratelimiting.bucket4j.dynamodb.v2.proxymanager

import io.github.bucket4j.distributed.proxy.ClientSideConfig
import io.github.bucket4j.distributed.proxy.generic.compare_and_swap.AsyncCompareAndSwapOperation
import io.github.bucket4j.distributed.proxy.generic.compare_and_swap.CompareAndSwapOperation
import misk.ratelimiting.bucket4j.dynamodb.v2.transaction.BaseDynamoDBTransaction.Companion.DEFAULT_KEY_NAME
import misk.ratelimiting.bucket4j.dynamodb.v2.transaction.LongDynamoDBTransaction
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.util.concurrent.CompletableFuture

internal class LongDynamoDBProxyManager(
  dynamoDb: DynamoDbClient,
  table: String,
  config: ClientSideConfig
) : BaseDynamoDBProxyManager<Long>(dynamoDb, table, config) {
  override fun beginCompareAndSwapOperation(key: Long): CompareAndSwapOperation =
    LongDynamoDBTransaction(key, dynamoDb, table)

  override fun beginAsyncCompareAndSwapOperation(key: Long?): AsyncCompareAndSwapOperation {
    throw UnsupportedOperationException()
  }

  override fun removeProxy(key: Long?) {
    val attributes = mapOf(DEFAULT_KEY_NAME to AttributeValue.fromN(key.toString()))

    dynamoDb.deleteItem {
      it.tableName(table)
      it.key(attributes)
    }
  }

  override fun isAsyncModeSupported() = false

  override fun removeAsync(key: Long?): CompletableFuture<Void> {
    throw UnsupportedOperationException()
  }
}
