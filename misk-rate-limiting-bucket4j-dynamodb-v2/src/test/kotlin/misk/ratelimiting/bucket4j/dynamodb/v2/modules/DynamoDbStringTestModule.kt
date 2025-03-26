package misk.ratelimiting.bucket4j.dynamodb.v2.modules

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import misk.MiskTestingServiceModule
import misk.aws2.dynamodb.testing.DockerDynamoDbModule
import misk.aws2.dynamodb.testing.DynamoDbTable
import misk.environment.DeploymentModule
import misk.inject.ReusableTestModule
import misk.ratelimiting.bucket4j.dynamodb.v2.DyStringRateLimitBucket
import misk.ratelimiting.bucket4j.dynamodb.v2.DynamoDbV2Bucket4jRateLimiterModule
import wisp.deployment.TESTING

class DynamoDbStringTestModule : ReusableTestModule() {
  override fun configure() {
    install(
      DockerDynamoDbModule(
        DynamoDbTable(STRING_TABLE_NAME, DyStringRateLimitBucket::class)
      )
    )
    install(DynamoDbV2Bucket4jRateLimiterModule(STRING_TABLE_NAME))
    install(MiskTestingServiceModule())
    install(DeploymentModule(TESTING))
    bind<MeterRegistry>().toInstance(SimpleMeterRegistry())
  }

  companion object {
    const val STRING_TABLE_NAME = "string_rate_limit_buckets"
  }
}
