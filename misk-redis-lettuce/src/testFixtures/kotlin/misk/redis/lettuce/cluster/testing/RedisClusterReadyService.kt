package misk.redis.lettuce.cluster.testing

import com.google.common.util.concurrent.AbstractIdleService
import io.lettuce.core.AbstractRedisClient
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.codec.StringCodec
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import misk.backoff.ExponentialBackoff
import misk.backoff.RetryConfig
import misk.backoff.retryableFuture
import misk.logging.getLogger
import misk.redis.lettuce.thenComposeUsing

/**
 * A service that ensures Redis clusters are ready before application startup.
 *
 * This service is designed for testing environments where Redis clusters need to be verified as operational before
 * tests begin. It:
 * 1. Attempts to connect to all Redis cluster clients with exponential backoff
 * 2. Waits for each cluster to be fully ready (all slots assigned and healthy)
 * 3. Blocks startup until all clusters are ready or timeout is reached
 *
 * The service will:
 * - Retry client connections for up to 10 seconds with exponential backoff (10ms to 5s)
 * - Wait up to 30 seconds for each cluster to be ready after connection
 * - Log connection attempts and cluster readiness status
 *
 * @property clients The set of Redis clients to verify. Only [RedisClusterClient] instances are processed.
 */
@Singleton
class RedisClusterReadyService @Inject constructor(private val clients: Set<AbstractRedisClient>) :
  AbstractIdleService() {
  override fun startUp() {
    logger.info { "Starting RedisClusterReadyService" }
    clients
      .mapNotNull { it as? RedisClusterClient }
      .map { client ->
        // Retry connecting each client for 10 seconds with backoff
        retryableFuture(
            RetryConfig.Builder(
                upTo = Int.MAX_VALUE,
                withBackoff =
                  ExponentialBackoff(baseDelay = { Duration.ofMillis(10) }, maxDelay = { Duration.ofSeconds(5) }),
              )
              .onRetry { count, e -> logger.info(e) { "retrying Redis client connect ($count) " } }
              .build()
          ) {
            client
              .runCatching { connect(StringCodec.UTF8) }
              .fold(
                onSuccess = { CompletableFuture.completedFuture(it) },
                onFailure = { CompletableFuture.failedFuture(it) },
              )
          }
          // Once connected, wait for up to 30 seconds for the cluster to be ready
          .thenComposeUsing { it.waitForRedisClusterReady(timeout = 30.seconds, pollInterval = 100.milliseconds) }
          .thenAccept { logger.info { "Cluster is ready with $it slots" } }
          .toCompletableFuture()
      }
      .let { CompletableFuture.allOf(*it.toTypedArray()).join() }
  }

  override fun shutDown() {
    logger.info { "Stopping RedisClusterReadyService" }
  }

  companion object {
    internal val logger = getLogger<RedisClusterReadyService>()
  }
}
