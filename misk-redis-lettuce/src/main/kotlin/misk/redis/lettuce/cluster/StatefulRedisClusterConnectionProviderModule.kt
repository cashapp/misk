package misk.redis.lettuce.cluster

import com.google.inject.TypeLiteral
import com.google.inject.multibindings.Multibinder
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.support.AsyncConnectionPoolSupport
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import misk.inject.toKey
import misk.redis.lettuce.RedisConnectionPoolConfig
import misk.redis.lettuce.connectionProviderTypeLiteral
import misk.redis.lettuce.metrics.RedisClientMetrics
import misk.redis.lettuce.toBoundedPoolConfig

/**
 * Internal module for configuring and binding [StatefulRedisClusterConnectionProvider]s.
 *
 * This module is responsible for setting up individual Redis cluster connection providers using
 * [PooledStatefulRedisClusterConnectionProvider]. One [StatefulRedisClusterConnectionProviderModule] should be
 * installed for each desired binding.
 *
 * This module does the following:
 * 1. **Creates a binding from** [connectionProviderType] **to a** [PooledStatefulRedisClusterConnectionProvider]
 *    **instance:**
 *         - If the [Annotation] is non-null, it will be used to qualify the binding.
 *         - The [RedisConnectionPoolConfig] is used to configure the underlying connection pool.
 *         - The provided [clientName] will be used to set the connection's client name.
 *         - The [RedisNodeConfig], [RedisCodec], and [useSsl] will be used to configure the connection's provider function.
 *         - The [PooledConnectionProvider] is registered as a provider for the ConnectionPool Metrics
 *     2. **Adds a binding for the** [StatefulRedisClusterConnectionProvider] **to a multibinder set**.
 *         - Provides access to all [StatefulRedisClusterConnectionProvider]s for lifecycle management.
 *
 * The provider supports both exclusive and shared connections:
 * - Exclusive connections are acquired from the pool and returned when closed
 * - A single shared connection is maintained for non-exclusive operations
 */
internal class StatefulRedisClusterConnectionProviderModule<
  K : Any,
  V : Any,
  T : StatefulRedisClusterConnectionProvider<K, V>,
>(
  private val connectionProviderType: TypeLiteral<T>,
  private val replicationGroupId: String,
  private val clientName: String,
  private val connectionPoolConfig: RedisConnectionPoolConfig,
  private val codec: RedisCodec<K, V>,
  private val annotation: Annotation?,
) : KAbstractModule() {

  override fun configure() {
    val clientKey = keyOf<RedisClusterClient>(annotation)
    val redisClientProvider = getProvider(clientKey)
    val clientMetricsProvider = getProvider(RedisClientMetrics::class.java)

    val connectionProviderKey = connectionProviderType.toKey(annotation)

    bind(connectionProviderKey)
      .toProvider {
        @Suppress("UNCHECKED_CAST")
        PooledStatefulRedisClusterConnectionProvider(
          AsyncConnectionPoolSupport.createBoundedObjectPoolAsync(
              object : Supplier<CompletionStage<StatefulRedisClusterConnection<K, V>>> {
                val count = AtomicInteger(0)

                override fun get(): CompletableFuture<StatefulRedisClusterConnection<K, V>> {
                  val suffix = count.incrementAndGet().toString().padStart(5, '0')
                  return try {
                    redisClientProvider.get().apply { partitions }.connectAsync(codec).maybeSetClientName(suffix)
                  } catch (e: Exception) {
                    CompletableFuture.failedFuture(e)
                  }
                }
              },
              connectionPoolConfig.toBoundedPoolConfig(),
              false, // this is handled directly in the connection provider
            )
            .thenApply {
              clientMetricsProvider.get().registerConnectionPoolMetrics(clientName, replicationGroupId, it)
              it
            }
            .toCompletableFuture(),
          replicationGroupId,
        )
          as T
      }
      .asSingleton()

    Multibinder.newSetBinder(binder(), connectionProviderTypeLiteral).addBinding().to(connectionProviderKey)
  }

  /**
   * Composer for a [StatefulRedisClusterConnection] [CompletableFuture] that will set the client name once the
   * connection is established and return the original [CompletableFuture]
   */
  @Suppress("UNCHECKED_CAST")
  private fun <K, V> CompletableFuture<StatefulRedisClusterConnection<K, V>>.maybeSetClientName(
    suffix: String? = null
  ): CompletableFuture<StatefulRedisClusterConnection<K, V>> =
    ("$replicationGroupId:$clientName${suffix?.let { ":$it" } ?: ""}" as? K)?.let {
      thenCompose { connection -> connection.async().clientSetname(it).thenApply { connection } }
    } ?: this
}
