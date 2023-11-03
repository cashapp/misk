package misk.ratelimiting.bucket4j.dynamodb.v1

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.google.inject.Provides
import io.github.bucket4j.distributed.proxy.ClientSideConfig
import io.github.bucket4j.distributed.proxy.ProxyManager
import io.github.bucket4j.dynamodb.v1.DynamoDBProxyManager
import io.micrometer.core.instrument.MeterRegistry
import jakarta.inject.Singleton
import misk.inject.KAbstractModule
import wisp.ratelimiting.RateLimiter
import wisp.ratelimiting.bucket4j.Bucket4jRateLimiter
import wisp.ratelimiting.bucket4j.ClockTimeMeter
import java.time.Clock

/**
 * Configures a [RateLimiter] that uses DynamoDb as a backend.
 */
class DynamoDbV1Bucket4jRateLimiterModule(
  private val tableName: String
) : KAbstractModule() {
  override fun configure() {
    requireBinding<Clock>()
    requireBinding<AmazonDynamoDB>()
    requireBinding<MeterRegistry>()
  }

  @Provides @Singleton
  fun providedRateLimiter(
    clock: Clock,
    dynamoDB: AmazonDynamoDB,
    meterRegistry: MeterRegistry
  ): RateLimiter {
    val proxyManager: ProxyManager<String> = DynamoDBProxyManager.stringKey(
      dynamoDB,
      tableName,
      ClientSideConfig.getDefault().withClientClock(ClockTimeMeter(clock))
    )
    return Bucket4jRateLimiter(proxyManager, clock, meterRegistry)
  }
}
