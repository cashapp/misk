package misk.redis.lettuce

import com.google.common.util.concurrent.AbstractIdleService
import io.lettuce.core.AbstractRedisClient
import io.lettuce.core.RedisClient
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.codec.StringCodec
import io.prometheus.client.Gauge
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import misk.backoff.ExponentialBackoff
import misk.backoff.RetryConfig
import misk.backoff.retryableFuture
import misk.logging.getLogger

/**
 * Service that manages the lifecycle of Redis standalone connections.
 *
 * This service is responsible for ensuring proper initialization and cleanup of Redis connections in standalone and
 * cluster mode. It implements [AbstractIdleService] to integrate with Misk's service management framework and handles
 * the following responsibilities:
 * - Connection validation during startup
 * - Graceful shutdown of all Redis connections
 * - Management of multiple connection providers
 * - Automatic loading of Redis Function code
 *
 * The service works with both read-write and read-only connections, managed through [ConnectionProvider]s. During
 * startup, it validates each connection by attempting to establish connectivity, ensuring the Redis instance is
 * available before the application fully starts.
 *
 * Lifecycle behavior:
 * - **Startup**:
 *     - Validates all configured Redis connections
 *     - Ensures connectivity to Redis instances
 *     - Fails fast if connections cannot be established
 *     - If there is a configured ResourcePath for function code, load it
 *     - For non-essential Redis (see [RedisModule]), none of the above blocks or fails startup: connectivity is
 *       verified in the background, retrying until Redis is reachable, and function code is loaded after that, also
 *       retrying until it succeeds or Redis rejects the code
 * - **Shutdown**:
 *     - Gracefully closes all Redis connections
 *     - Waits for all close operations to complete
 *     - Ensures proper cleanup of resources
 *
 * The service automatically handles both read-write and read-only connections, supporting Redis replication
 * configurations where different endpoints may be used for different operation types.
 */
@Singleton
class RedisService
@Inject
internal constructor(
  private val clients: Set<AbstractRedisClient>,
  private val connectionProviders: Set<ConnectionProvider<*, *, *>>,
  private val functionCodeLoaders: Set<FunctionCodeLoader>,
  private val nonEssentialClients: Set<NonEssentialRedisClient>,
) : AbstractIdleService() {

  constructor(
    clients: Set<AbstractRedisClient>,
    connectionProviders: Set<ConnectionProvider<*, *, *>>,
    functionCodeLoaders: Set<FunctionCodeLoader>,
  ) : this(clients, connectionProviders, functionCodeLoaders, emptySet())

  @Volatile private var stopping = false

  override fun startUp() {
    logger.info { "Starting RedisService for ${connectionProviders.size} connections" }

    // Get a connection from each ConnectionProvider and verify the configuration
    (clients - nonEssentialClients.map { it.client }.toSet())
      .map { client ->
        retryableFuture(verifyRetry) { client.verify().toCompletableFuture() }.orTimeout(30, TimeUnit.SECONDS)
      }
      .joinAll()

    // Load the  function code for the supplied loaders
    functionCodeLoaders.map { it.load() }.joinAll()

    nonEssentialClients.forEach { verifyInBackground(it) }
  }

  override fun shutDown() {
    logger.info { "Stopping RedisService for ${connectionProviders.size} connections" }

    stopping = true
    // Close all the ConnectionProviders
    connectionProviders.map { it.closeAsync() }.joinAll()
    // Shutdown all the clients
    clients.map { it.shutdownAsync() }.joinAll()
  }

  private fun AbstractRedisClient.verify(): CompletionStage<Void> =
    when (this) {
      is RedisClient ->
        CompletableFuture.completedFuture(connect(StringCodec.UTF8)).thenComposeUsing { it.async().ping() }

      is RedisClusterClient ->
        CompletableFuture.completedFuture(connect(StringCodec.UTF8)).thenComposeUsing { it.async().ping() }

      else -> CompletableFuture.completedFuture("PONG")
    }.thenAccept(::checkPong)

  private fun checkPong(reply: String) =
    check(reply.equals("PONG", ignoreCase = true)) { "redis 'ping' should return 'PONG'" }

  private fun verifyInBackground(nonEssentialClient: NonEssentialRedisClient) {
    val replicationGroupId = nonEssentialClient.replicationGroupId
    nonEssentialClient.startupVerified.set(0.0)
    retryUntilSuccess(
        backoff = ExponentialBackoff(baseDelay = Duration.ofMillis(100), maxDelay = Duration.ofSeconds(10)),
        shouldRetry = { !stopping },
        onRetry = { failures, e ->
          if (failures == 1) {
            logger.warn(e) {
              "Non-essential Redis '$replicationGroupId' is unreachable, continuing startup and retrying in the background"
            }
          }
        },
      ) {
        nonEssentialClient.ping().thenAccept(::checkPong)
      }
      .thenCompose {
        nonEssentialClient.startupVerified.set(1.0)
        logger.info { "Connected to non-essential Redis '$replicationGroupId'" }
        nonEssentialClient.functionCodeLoader?.loadWithRetries(
          shouldRetry = { !stopping },
          onRetry = { failures, e ->
            if (failures == 1) {
              logger.warn(e) {
                "Failed loading function code to non-essential Redis '$replicationGroupId', retrying in the background"
              }
            }
          },
        ) ?: CompletableFuture.completedFuture(null)
      }
      .whenComplete { _, e ->
        if (e != null && !stopping) {
          logger.error(e) { "Failed setting up non-essential Redis '$replicationGroupId' in the background" }
        }
      }
  }

  private fun <T> List<CompletionStage<T>>.joinAll() =
    CompletableFuture.allOf(*this.map { it.toCompletableFuture() }.toTypedArray()).join()

  companion object {
    private val logger = getLogger<RedisService>()
    private val verifyRetry =
      RetryConfig.Builder(
          upTo = Int.MAX_VALUE,
          withBackoff = ExponentialBackoff(baseDelay = { Duration.ofMillis(10) }, maxDelay = { Duration.ofSeconds(5) }),
        )
        .build()
  }
}

/**
 * A Redis client the app can serve without. Instead of verifying it during startup, [RedisService] retries [ping] in
 * the background until it succeeds, then loads its [functionCodeLoader], if any.
 */
internal class NonEssentialRedisClient(
  val replicationGroupId: String,
  val client: AbstractRedisClient,
  /** Connects and pings without blocking the calling thread. */
  val ping: () -> CompletionStage<String>,
  val functionCodeLoader: RetryingFunctionCodeLoader?,
  val startupVerified: Gauge.Child,
)
