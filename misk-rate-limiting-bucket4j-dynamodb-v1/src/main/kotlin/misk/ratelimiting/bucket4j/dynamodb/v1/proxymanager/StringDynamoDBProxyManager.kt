package misk.ratelimiting.bucket4j.dynamodb.v1.proxymanager

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.DeleteItemRequest
import com.amazonaws.services.dynamodbv2.model.PutItemRequest
import io.github.bucket4j.distributed.proxy.ClientSideConfig
import io.github.bucket4j.distributed.proxy.generic.compare_and_swap.AsyncCompareAndSwapOperation
import io.github.bucket4j.distributed.proxy.generic.compare_and_swap.CompareAndSwapOperation
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import misk.ratelimiting.bucket4j.dynamodb.v1.transaction.BaseDynamoDBTransaction.Companion.DEFAULT_KEY_NAME
import misk.ratelimiting.bucket4j.dynamodb.v1.transaction.StringDynamoDBTransaction

internal class StringDynamoDBProxyManager(dynamoDb: AmazonDynamoDB, table: String, config: ClientSideConfig) :
  BaseDynamoDBProxyManager<String>(dynamoDb, table, config) {
  override fun beginCompareAndSwapOperation(key: String): CompareAndSwapOperation =
    StringDynamoDBTransaction(key, dynamoDb, table)

  override fun beginAsyncCompareAndSwapOperation(key: String?): AsyncCompareAndSwapOperation {
    throw UnsupportedOperationException()
  }

  override fun removeProxy(key: String?) {
    val attributes = mapOf(DEFAULT_KEY_NAME to AttributeValue().withS(key))
    val deleteItemRequest =
      DeleteItemRequest(table, attributes).apply {
        clientSideConfig.requestTimeoutNanos.ifPresent {
          withSdkRequestTimeout<PutItemRequest>(TimeUnit.NANOSECONDS.toMillis(it).toInt())
        }
      }

    dynamoDb.deleteItem(deleteItemRequest)
  }

  override fun isAsyncModeSupported() = false

  override fun removeAsync(key: String?): CompletableFuture<Void> {
    throw UnsupportedOperationException()
  }
}
