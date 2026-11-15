package misk.ratelimiting.bucket4j.dynamodb.v2.proxymanager

import io.github.bucket4j.distributed.proxy.ClientSideConfig
import io.github.bucket4j.distributed.proxy.generic.compare_and_swap.AbstractCompareAndSwapBasedProxyManager
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

internal abstract class BaseDynamoDBProxyManager<K>
protected constructor(protected val dynamoDb: DynamoDbClient, protected val table: String, config: ClientSideConfig) :
  AbstractCompareAndSwapBasedProxyManager<K>(config)
