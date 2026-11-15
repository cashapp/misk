package misk.redis.lettuce.cluster

import com.google.inject.Provider
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.codec.StringCodec
import java.util.concurrent.CompletionStage
import misk.redis.lettuce.FunctionCodeLoader
import misk.redis.lettuce.RetryingFunctionCodeLoader
import misk.redis.lettuce.thenComposeUsing

/**
 * Implementation of [FunctionCodeLoader] for Redis cluster deployments.
 *
 * This implementation:
 * - Loads functions from [RedisClusterGroupConfig.functionCodeFilePath]
 * - Manages function loading across all cluster nodes
 * - Ensures consistent function availability in a sharded environment
 *
 * In a cluster deployment, functions must be loaded on all master nodes to ensure availability regardless of which node
 * handles the request. This implementation handles the complexity of distributing functions across the cluster
 * topology.
 *
 * @see [FunctionCodeLoader]
 * @see <a href="https://redis.io/docs/management/scaling/#redis-cluster-specification">Redis Cluster Specification</a>
 * @see <a href="https://redis.io/docs/reference/cluster-spec/">Redis Cluster Technical Specification</a>
 */
internal class ClusterFunctionCodeLoader(
  private val clientProvider: Provider<RedisClusterClient>,
  codeResourcePath: String,
  private val replace: Boolean = true,
) : RetryingFunctionCodeLoader(codeResourcePath) {
  override val destination = "cluster"

  override fun loadOnce(code: String): CompletionStage<String> {
    val client = clientProvider.get()
    return client.refreshPartitionsAsync().thenCompose {
      client.connectAsync(StringCodec.UTF8).thenComposeUsing { connection ->
        // Cluster nodes don't share functions, and a keyless FUNCTION LOAD only reaches one node, so load on every
        // primary. Replicas get the functions from their primary.
        connection.async().upstream().commands().functionLoad(code, replace).thenApply { libraries ->
          libraries.first()
        }
      }
    }
  }
}
