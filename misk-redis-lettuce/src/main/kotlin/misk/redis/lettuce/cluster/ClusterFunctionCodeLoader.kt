package misk.redis.lettuce.cluster

import com.google.common.io.Resources.getResource
import com.google.inject.Provider
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.codec.StringCodec
import misk.backoff.ExponentialBackoff
import misk.backoff.RetryConfig
import misk.backoff.retryableFuture
import misk.redis.lettuce.FunctionCodeLoader
import misk.redis.lettuce.thenComposeUsing
import misk.logging.getLogger
import java.time.Duration
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import com.google.common.io.Resources.toString as readAsString

/**
 * Implementation of [FunctionCodeLoader] for Redis cluster deployments.
 * 
 * This implementation:
 * - Loads functions from [RedisClusterGroupConfig.functionCodeFilePath]
 * - Manages function loading across all cluster nodes
 * - Ensures consistent function availability in a sharded environment
 * 
 * In a cluster deployment, functions must be loaded on all master nodes to ensure
 * availability regardless of which node handles the request. This implementation
 * handles the complexity of distributing functions across the cluster topology.
 * 
 * @see [FunctionCodeLoader]
 * @see <a href="https://redis.io/docs/management/scaling/#redis-cluster-specification">Redis Cluster Specification</a>
 * @see <a href="https://redis.io/docs/reference/cluster-spec/">Redis Cluster Technical Specification</a>
 */
internal class ClusterFunctionCodeLoader(
  private val clientProvider: Provider<RedisClusterClient>,
  private val codeResourcePath: String,
  private val replace: Boolean = true
) : FunctionCodeLoader {
  override fun load(): CompletionStage<Void> {
    val code = readAsString(getResource(codeResourcePath), Charsets.UTF_8)
    val client = clientProvider.get()
    return retryableFuture(retry){
      client.refreshPartitionsAsync().thenCompose {
        client.connectAsync(StringCodec.UTF8).thenComposeUsing { connection ->
          connection.async().functionLoad(code, replace)
            .thenAccept { library ->
              logger.info { "Loaded Redis function code library '$library' from '$codeResourcePath'" }
            }
        }
      }.toCompletableFuture()
    }.orTimeout(30, TimeUnit.SECONDS)
      .whenComplete { _, e ->
        if (e != null) {
          logger.error(e) { "Failed loading function code $codeResourcePath to cluster" }
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
