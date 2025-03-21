package misk.ratelimiting.bucket4j.dynamodb.v2.proxymanager

import io.github.bucket4j.distributed.proxy.ClientSideConfig
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

internal class DynamoDBProxyManager private constructor() {
  companion object {
    fun stringKey(
      dynamoDB: DynamoDbClient,
      table: String,
      config: ClientSideConfig
    ): BaseDynamoDBProxyManager<String> =
      StringDynamoDBProxyManager(dynamoDB, table, config)

    fun longKey(
      dynamoDB: DynamoDbClient,
      table: String,
      config: ClientSideConfig
    ): BaseDynamoDBProxyManager<Long> =
      LongDynamoDBProxyManager(dynamoDB, table, config)
  }
}
