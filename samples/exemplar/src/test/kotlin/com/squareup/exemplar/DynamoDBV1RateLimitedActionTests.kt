package com.squareup.exemplar

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.google.inject.Module
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import jakarta.inject.Inject
import misk.aws.dynamodb.testing.DockerDynamoDbModule
import misk.aws.dynamodb.testing.DynamoDbTable
import misk.inject.KAbstractModule
import misk.ratelimiting.bucket4j.dynamodb.v1.DynamoDbV1Bucket4jRateLimiterModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule

@MiskTest(startService = true)
class DynamoDBV1RateLimitedActionTests : AbstractRateLimitedActionTests() {
  @Suppress("unused")
  @MiskTestModule
  val module: Module =
    object : KAbstractModule() {
      override fun configure() {
        install(ExemplarTestModule())
        install(DockerDynamoDbModule(DynamoDbTable(DyRateLimitBucket::class)))
        install(DynamoDbV1Bucket4jRateLimiterModule("rate_limit_buckets"))
        bind<MeterRegistry>().toInstance(SimpleMeterRegistry())
      }
    }

  @Inject private lateinit var dynamoDb: AmazonDynamoDB

  override fun setException() {
    dynamoDb.shutdown()
  }
}
