package misk.ratelimiting.bucket4j.dynamodb.v1.proxymanager

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import io.github.bucket4j.distributed.proxy.ClientSideConfig
import io.github.bucket4j.distributed.proxy.generic.compare_and_swap.AbstractCompareAndSwapBasedProxyManager

internal abstract class BaseDynamoDBProxyManager<K>
protected constructor(protected val dynamoDb: AmazonDynamoDB, protected val table: String, config: ClientSideConfig) :
  AbstractCompareAndSwapBasedProxyManager<K>(config)
