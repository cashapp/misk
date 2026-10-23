package misk.redis.lettuce.standalone

import com.google.inject.Provider
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.codec.StringCodec
import java.util.concurrent.CompletionStage
import misk.redis.lettuce.FunctionCodeLoader
import misk.redis.lettuce.RetryingFunctionCodeLoader
import misk.redis.lettuce.thenComposeUsing

/**
 * Implementation of [FunctionCodeLoader] for Redis standalone and replication deployments.
 *
 * This implementation:
 * - Loads functions from [RedisReplicationGroupConfig.functionCodeFilePath]
 * - Uses a single Redis connection to load functions
 * - Automatically replicates loaded functions to replica nodes through Redis replication
 *
 * Functions loaded through this implementation are automatically propagated to all replica nodes in the replication
 * group, ensuring consistent function availability across the deployment.
 *
 * @see [FunctionCodeLoader]
 * @see <a href="https://redis.io/docs/management/replication/">Redis Replication Documentation</a>
 */
internal class StandaloneFunctionCodeLoader(
  private val clientProvider: Provider<RedisClient>,
  private val uri: RedisURI,
  codeResourcePath: String,
  private val replace: Boolean = true,
) : RetryingFunctionCodeLoader(codeResourcePath) {
  override val destination
    get() = uri.toString()

  override fun loadOnce(code: String): CompletionStage<String> =
    clientProvider.get().connectAsync(StringCodec.UTF8, uri).thenComposeUsing { it.async().functionLoad(code, replace) }
}
