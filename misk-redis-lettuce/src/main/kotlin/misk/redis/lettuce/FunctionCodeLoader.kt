package misk.redis.lettuce

import com.google.common.io.Resources.getResource
import com.google.common.io.Resources.toString as readAsString
import io.lettuce.core.RedisBusyException
import io.lettuce.core.RedisCommandExecutionException
import io.lettuce.core.RedisLoadingException
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import misk.backoff.ExponentialBackoff
import misk.logging.getLogger
import misk.redis.lettuce.cluster.ClusterFunctionCodeLoader
import misk.redis.lettuce.standalone.StandaloneFunctionCodeLoader

/**
 * Interface for loading Redis Functions from a file path specified in Redis configuration.
 *
 * Redis Functions are server-side Lua scripts that provide several advantages over traditional EVAL scripts:
 * - Better performance through automatic caching and compilation of functions
 * - Improved security with proper isolation and memory management
 * - Library organization capabilities for better code reuse and maintenance
 * - Built-in versioning and dependency management
 * - Type safety through function signatures
 * - Atomic execution guarantees
 *
 * This loader is responsible for:
 * 1. Reading function code from the file specified in [RedisReplicationGroupConfig.functionCodeFilePath] and/or
 *    [RedisClusterGroupConfig.functionCodeFilePath]
 * 2. Loading the functions into Redis using the FUNCTION LOAD command
 * 3. Managing function registration and updates
 *
 * Functions loaded through this interface are persistent across Redis restarts and are automatically replicated to
 * replica nodes, ensuring consistency across the Redis deployment.
 *
 * Two implementations are available:
 * - [StandaloneFunctionCodeLoader] for Redis standalone/replication deployments
 * - [ClusterFunctionCodeLoader] for Redis cluster deployments
 *
 * @see [StandaloneFunctionCodeLoader]
 * @see [ClusterFunctionCodeLoader]
 * @see <a href="https://redis.io/docs/latest/develop/interact/programmability/functions-intro/">Redis Functions
 *   Documentation</a>
 * @see <a href="https://redis.io/commands/function-load/">FUNCTION LOAD Command</a>
 * @see <a href="https://redis.io/docs/management/scaling/">Redis Scaling Documentation</a>
 */
interface FunctionCodeLoader {
  fun load(): CompletionStage<Void>
}

/**
 * Loads the code in [codeResourcePath], retrying [loadOnce] while it fails transiently, for example while Redis is
 * unreachable. An error reply from Redis, such as code that doesn't compile or a missing ACL permission, fails without
 * retrying.
 */
internal abstract class RetryingFunctionCodeLoader(private val codeResourcePath: String) : FunctionCodeLoader {
  /** Where the code is loaded, for logging. */
  protected abstract val destination: String

  /** Makes one attempt to load [code], returning the name of the loaded library. */
  protected abstract fun loadOnce(code: String): CompletionStage<String>

  /** Gives up after 30 seconds, so [RedisService] doesn't wait on it forever during startup. */
  override fun load(): CompletionStage<Void> =
    loadWithRetries(shouldRetry = { true }).orTimeout(30, TimeUnit.SECONDS).whenComplete { _, e ->
      if (e != null) {
        logger.error(e) { "Failed loading Redis function code in $codeResourcePath to $destination" }
      }
    }

  /** Retries until the code is loaded, Redis rejects it, or [shouldRetry] returns false. */
  fun loadWithRetries(
    shouldRetry: (Throwable) -> Boolean,
    onRetry: (failures: Int, error: Throwable) -> Unit = { _, _ -> },
  ): CompletableFuture<Void> {
    val code = readAsString(getResource(codeResourcePath), Charsets.UTF_8)
    // Returns retryUntilSuccess's own future, so completing it, e.g. with orTimeout, also stops the retries.
    return retryUntilSuccess(
      backoff = ExponentialBackoff(baseDelay = Duration.ofMillis(10), maxDelay = Duration.ofSeconds(5)),
      shouldRetry = { isRetryableFunctionLoadError(it) && shouldRetry(it) },
      onRetry = onRetry,
    ) {
      loadOnce(code).thenAccept { library ->
        logger.info { "Loaded Redis function code library '$library' from '$codeResourcePath'" }
      }
    }
  }

  companion object {
    private val logger = getLogger<FunctionCodeLoader>()
  }
}

/** Error replies from Redis won't succeed on retry, except while it's loading its dataset or busy running a script. */
internal fun isRetryableFunctionLoadError(error: Throwable) =
  error !is RedisCommandExecutionException || error is RedisLoadingException || error is RedisBusyException
