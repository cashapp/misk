package misk.ratelimiting.bucket4j.dynamodb.v2.proxymanager

import io.github.bucket4j.distributed.proxy.ClientSideConfig
import io.github.bucket4j.distributed.proxy.generic.compare_and_swap.AsyncCompareAndSwapOperation
import io.github.bucket4j.distributed.proxy.generic.compare_and_swap.CompareAndSwapOperation
import java.util.concurrent.CompletableFuture
import misk.ratelimiting.bucket4j.dynamodb.v2.transaction.BaseDynamoDBTransaction.Companion.DEFAULT_KEY_NAME
import misk.ratelimiting.bucket4j.dynamodb.v2.transaction.StringDynamoDBTransaction
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

internal class StringDynamoDBProxyManager(dynamoDb: DynamoDbClient, table: String, config: ClientSideConfig) :
  BaseDynamoDBProxyManager<String>(dynamoDb, table, config) {
  override fun beginCompareAndSwapOperation(key: String): CompareAndSwapOperation =
    StringDynamoDBTransaction(key, dynamoDb, table)

  override fun beginAsyncCompareAndSwapOperation(key: String?): AsyncCompareAndSwapOperation {
    throw UnsupportedOperationException()
  }

  override fun removeProxy(key: String?) {
    val attributes = mapOf(DEFAULT_KEY_NAME to AttributeValue.fromS(key))

    dynamoDb.deleteItem {
      it.tableName(table)
      it.key(attributes)
    }
  }

  override fun isAsyncModeSupported() = false

  override fun removeAsync(key: String?): CompletableFuture<Void> {
    throw UnsupportedOperationException()
  }
}
