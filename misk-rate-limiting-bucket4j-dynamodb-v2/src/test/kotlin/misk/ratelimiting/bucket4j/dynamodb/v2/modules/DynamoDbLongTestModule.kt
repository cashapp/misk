package misk.ratelimiting.bucket4j.dynamodb.v2.modules

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import misk.MiskTestingServiceModule
import misk.aws2.dynamodb.testing.DockerDynamoDbModule
import misk.aws2.dynamodb.testing.DynamoDbTable
import misk.environment.DeploymentModule
import misk.inject.ReusableTestModule
import misk.ratelimiting.bucket4j.dynamodb.v2.DyLongRateLimitBucket
import misk.ratelimiting.bucket4j.dynamodb.v2.DynamoDbV2Bucket4jRateLimiterModule
import wisp.deployment.TESTING

class DynamoDbLongTestModule : ReusableTestModule() {
  override fun configure() {
    install(
      DockerDynamoDbModule(
        DynamoDbTable(LONG_TABLE_NAME, DyLongRateLimitBucket::class)
      )
    )
    install(DynamoDbV2Bucket4jRateLimiterModule(LONG_TABLE_NAME))
    install(MiskTestingServiceModule())
    install(DeploymentModule(TESTING))
    bind<MeterRegistry>().toInstance(SimpleMeterRegistry())
  }

  companion object {
    const val LONG_TABLE_NAME = "long_rate_limit_buckets"
  }
}
