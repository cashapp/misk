package misk.ratelimiting.bucket4j.dynamodb.v2

import com.google.inject.Provides
import io.github.bucket4j.distributed.proxy.ClientSideConfig
import io.github.bucket4j.distributed.proxy.ProxyManager
import io.micrometer.core.instrument.MeterRegistry
import jakarta.inject.Singleton
import java.time.Clock
import java.time.Duration
import misk.inject.KAbstractModule
import misk.ratelimiting.bucket4j.dynamodb.v2.proxymanager.DynamoDBProxyManager
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import wisp.ratelimiting.RateLimitPruner
import wisp.ratelimiting.RateLimiter
import wisp.ratelimiting.bucket4j.Bucket4jRateLimiter
import wisp.ratelimiting.bucket4j.ClockTimeMeter

/** Configures a [RateLimiter] that uses DynamoDb as a backend. */
class DynamoDbV2Bucket4jRateLimiterModule
@JvmOverloads
constructor(
  private val tableName: String,
  private val prunerPageSize: Int = 1000,
  private val maxRetries: Int = 3,
  private val retryTimeout: Duration = Duration.ofMillis(25),
  private val configMutator: ClientSideConfig.() -> Unit = {},
) : KAbstractModule() {
  override fun configure() {
    requireBinding<Clock>()
    requireBinding<DynamoDbClient>()
    requireBinding<MeterRegistry>()
  }

  @Provides
  @Singleton
  fun providedRateLimiter(clock: Clock, dynamoDB: DynamoDbClient, meterRegistry: MeterRegistry): RateLimiter {
    val config =
      ClientSideConfig.getDefault()
        .withClientClock(ClockTimeMeter(clock))
        .withRequestTimeout(retryTimeout)
        .withMaxRetries(maxRetries)
        .apply { configMutator() }
    val proxyManager: ProxyManager<String> = DynamoDBProxyManager.stringKey(dynamoDB, tableName, config)
    return Bucket4jRateLimiter(proxyManager, clock, meterRegistry)
  }

  @Provides
  @Singleton
  fun providedPruner(clock: Clock, dynamoDB: DynamoDbClient, meterRegistry: MeterRegistry): RateLimitPruner {
    return DynamoDbV2BucketPruner(clock, dynamoDB, meterRegistry, tableName, prunerPageSize, retryTimeout)
  }
}
