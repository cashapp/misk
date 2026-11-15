package misk.ratelimiting.bucket4j.dynamodb.v1.proxymanager

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import io.github.bucket4j.distributed.proxy.ClientSideConfig

internal class DynamoDBProxyManager private constructor() {
  companion object {
    fun stringKey(dynamoDb: AmazonDynamoDB, table: String, config: ClientSideConfig): BaseDynamoDBProxyManager<String> =
      StringDynamoDBProxyManager(dynamoDb, table, config)

    fun longKey(dynamoDb: AmazonDynamoDB, table: String, config: ClientSideConfig): BaseDynamoDBProxyManager<Long> =
      LongDynamoDBProxyManager(dynamoDb, table, config)
  }
}
