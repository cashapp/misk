package com.squareup.exemplar

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.google.inject.Module
import com.google.inject.Provides
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.aws.dynamodb.testing.DockerDynamoDbModule
import misk.aws.dynamodb.testing.DynamoDbTable
import misk.inject.KAbstractModule
import misk.ratelimiting.bucket4j.dynamodb.v1.DynamoDbV1Bucket4jRateLimiterModule
import misk.ratelimiting.bucket4j.redis.RedisBucket4jRateLimiterModule
import misk.redis.testing.DockerRedis
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

@MiskTest(startService = true)
class DynamoDBV1RateLimitedActionTests : AbstractRateLimitedActionTests() {
  @Suppress("unused")
  @MiskTestModule val module: Module = object : KAbstractModule() {
    override fun configure() {
      install(ExemplarTestModule())
      install(
        DockerDynamoDbModule(
          DynamoDbTable(DyRateLimitBucket::class)
        )
      )
      install(DynamoDbV1Bucket4jRateLimiterModule("rate_limit_buckets"))
    }


    @Provides @Singleton
    // In prod this is provided by Skim
    fun provideMeterRegistry(collectorRegistry: CollectorRegistry): MeterRegistry {
      return PrometheusMeterRegistry(
        PrometheusConfig.DEFAULT, collectorRegistry, Clock.SYSTEM
      )
    }
  }

  @Inject private lateinit var dynamoDb: AmazonDynamoDB

  override fun setException() {
    dynamoDb.shutdown()
  }
}
