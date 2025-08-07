package misk.redis.lettuce


import com.google.common.util.concurrent.AbstractIdleService
import io.lettuce.core.AbstractRedisClient
import io.lettuce.core.RedisClient
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.codec.StringCodec
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.backoff.ExponentialBackoff
import misk.backoff.RetryConfig
import misk.backoff.retryableFuture
import misk.logging.getLogger
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit


/**
 * Service that manages the lifecycle of Redis standalone connections.
 *
 * This service is responsible for ensuring proper initialization and cleanup of Redis connections
 * in standalone and cluster mode. It implements [AbstractIdleService] to integrate with Misk's
 * service management framework and handles the following responsibilities:
 *
 * - Connection validation during startup
 * - Graceful shutdown of all Redis connections
 * - Management of multiple connection providers
 * - Automatic loading of Redis Function code
 *
 * The service works with both read-write and read-only connections, managed through
 * [ConnectionProvider]s. During startup, it validates each connection by attempting
 * to establish connectivity, ensuring the Redis instance is available before the application
 * fully starts.
 *
 * Lifecycle behavior:
 * - **Startup**:
 *   - Validates all configured Redis connections
 *   - Ensures connectivity to Redis instances
 *   - Fails fast if connections cannot be established
 *   - If there is a configured ResourcePath for function code, load it
 *
 * - **Shutdown**:
 *   - Gracefully closes all Redis connections
 *   - Waits for all close operations to complete
 *   - Ensures proper cleanup of resources
 *
 * The service automatically handles both read-write and read-only connections,
 * supporting Redis replication configurations where different endpoints may be
 * used for different operation types.
 *
 */
@Singleton
class RedisService @Inject constructor(
  private val clients: Set<AbstractRedisClient>,
  private val connectionProviders: Set<ConnectionProvider<*, *, *>>,
  private val functionCodeLoaders: Set<FunctionCodeLoader>
) : AbstractIdleService() {

  override fun startUp() {
    logger.info {
      "Starting RedisService for ${connectionProviders.size} connections"
    }

    // Get a connection from each ConnectionProvider and verify the configuration
    clients.map { client -> retryableFuture(verifyRetry) { client.verify().toCompletableFuture() }
      .orTimeout(30, TimeUnit.SECONDS) }
      .joinAll()

    // Load the  function code for the supplied loaders
    functionCodeLoaders.map { it.load() }.joinAll()
  }

  override fun shutDown() {
    logger.info {
      "Stopping RedisService for ${connectionProviders.size} connections"
    }

    // Close all the ConnectionProviders
    connectionProviders.map { it.closeAsync() }.joinAll()
    // Shutdown all the clients
    clients.map { it.shutdownAsync() }.joinAll()

  }

  private fun AbstractRedisClient.verify(): CompletionStage<Void> =
    when (this) {
      is RedisClient ->
        CompletableFuture.completedFuture(connect(StringCodec.UTF8))
          .thenComposeUsing { it.async().ping() }

      is RedisClusterClient ->
        CompletableFuture.completedFuture(connect(StringCodec.UTF8))
          .thenComposeUsing { it.async().ping() }

      else -> CompletableFuture.completedFuture("PONG")
    }.thenAccept { check(it.equals("PONG", ignoreCase = true)) { "redis 'ping' should return 'PONG'" } }

  private fun <T> List<CompletionStage<T>>.joinAll() =
    CompletableFuture.allOf(*this.map { it.toCompletableFuture() }.toTypedArray()).join()


  companion object {
    private val logger = getLogger<RedisService>()
    private val verifyRetry = RetryConfig.Builder(
      upTo = Int.MAX_VALUE,
      withBackoff = ExponentialBackoff(
        baseDelay = { Duration.ofMillis(10) },
        maxDelay = { Duration.ofSeconds(5) },
      ),
    ).build()
  }
}

