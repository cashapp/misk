package misk.redis.lettuce.standalone

import com.google.common.io.Resources.getResource
import com.google.inject.Provider
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.codec.StringCodec
import misk.backoff.ExponentialBackoff
import misk.backoff.RetryConfig
import misk.backoff.retryableFuture
import misk.redis.lettuce.FunctionCodeLoader
import misk.redis.lettuce.thenComposeUsing
import wisp.logging.getLogger
import java.time.Duration
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import com.google.common.io.Resources.toString as readAsString

/**
 * Implementation of [FunctionCodeLoader] for Redis standalone and replication deployments.
 * 
 * This implementation:
 * - Loads functions from [RedisReplicationGroupConfig.functionCodeFilePath]
 * - Uses a single Redis connection to load functions
 * - Automatically replicates loaded functions to replica nodes through Redis replication
 * 
 * Functions loaded through this implementation are automatically propagated to all replica nodes
 * in the replication group, ensuring consistent function availability across the deployment.
 * 
 * @see [FunctionCodeLoader]
 * @see <a href="https://redis.io/docs/management/replication/">Redis Replication Documentation</a>
 */
internal class StandaloneFunctionCodeLoader(
  private val clientProvider: Provider<RedisClient>,
  private val uri: RedisURI,
  private val codeResourcePath: String,
  private val replace: Boolean = true
) : FunctionCodeLoader {

  override fun load(): CompletionStage<Void> {
    val code = readAsString(getResource(codeResourcePath), Charsets.UTF_8)
    return retryableFuture(retry) {
      clientProvider.get().connectAsync(StringCodec.UTF8, uri).thenComposeUsing {
        it.async().functionLoad(code, replace).thenAccept { library ->
          logger.info { "Loaded Redis function code library '$library' from '$codeResourcePath'" }
        }
      }.toCompletableFuture()
    }.orTimeout(30, TimeUnit.SECONDS)
      .whenComplete { _, e ->
        if (e != null) {
          logger.error(e) {
            "Failed loading Redis function code library in $codeResourcePath to $uri"
          }
        }
      }
  }

  companion object {
    private val logger = getLogger<FunctionCodeLoader>()
    private val retry = RetryConfig.Builder(
      upTo = Int.MAX_VALUE,
      withBackoff = ExponentialBackoff(
        baseDelay = { Duration.ofMillis(10) },
        maxDelay = { Duration.ofSeconds(5) },
      ),
    ).build()
  }
}
