package misk.redis.lettuce.cluster.testing

import com.google.common.util.concurrent.AbstractIdleService
import io.lettuce.core.AbstractRedisClient
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.codec.StringCodec
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.backoff.ExponentialBackoff
import misk.backoff.RetryConfig
import misk.backoff.retryableFuture
import misk.redis.lettuce.thenComposeUsing
import wisp.logging.getLogger
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Singleton
class RedisClusterReadyService @Inject constructor(
  private val clients: Set<AbstractRedisClient>
) : AbstractIdleService() {
  override fun startUp() {
    logger.info {
      "Starting RedisClusterReadyService"
    }
    clients.mapNotNull { it as? RedisClusterClient }.map { client ->
      // Retry connecting each client for 10 seconds with backoff
      retryableFuture(
        RetryConfig.Builder(
          upTo = Int.MAX_VALUE,
          withBackoff = ExponentialBackoff(
            baseDelay = { Duration.ofMillis(10) },
            maxDelay = { Duration.ofSeconds(5) },
          ),
        ).onRetry { count, e ->
          logger.info(e) { "retrying Redis client connect ($count) " }
        }.build(),
      ) {
        client.runCatching { connect(StringCodec.UTF8) }.fold(
            onSuccess = { CompletableFuture.completedFuture(it) },
            onFailure = { CompletableFuture.failedFuture(it) },
        )
      }
        // Once connected, wait for up to 30 seconds for the cluster to be ready
        .thenComposeUsing {
          it.waitForRedisClusterReady(
            timeout = 30.seconds,
            pollInterval = 100.milliseconds,
          )
        }.thenAccept {
          logger.info { "Cluster is ready with $it slots" }
        }.toCompletableFuture()
    }.let { CompletableFuture.allOf(*it.toTypedArray()).join() }
  }

  override fun shutDown() {
    logger.info {
      "Stopping RedisClusterReadyService"
    }
  }

  companion object {
    internal val logger = getLogger<RedisClusterReadyService>()
  }
}


