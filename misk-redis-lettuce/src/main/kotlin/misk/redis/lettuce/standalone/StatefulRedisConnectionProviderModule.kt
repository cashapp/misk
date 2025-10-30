package misk.redis.lettuce.standalone

import com.google.inject.TypeLiteral
import com.google.inject.multibindings.Multibinder
import io.lettuce.core.ConnectionFuture
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.support.AsyncConnectionPoolSupport
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import misk.inject.toKey
import misk.redis.lettuce.RedisConnectionPoolConfig
import misk.redis.lettuce.RedisNodeConfig
import misk.redis.lettuce.connectionProviderTypeLiteral
import misk.redis.lettuce.redisUri
import misk.redis.lettuce.toBoundedPoolConfig
import misk.redis.lettuce.metrics.RedisClientMetrics
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

/**
 * Internal module for configuring and binding [StatefulRedisConnectionProvider]s.
 *
 * This module is responsible for setting up individual Redis connection providers using
 * [PooledStatefulRedisConnectionProvider]. One [StatefulRedisConnectionProviderModule] should
 * be installed for each desired binding.
 *
 * This module does the following:
 *
 * 1. **Creates a binding from** [connectionProviderType] **to a** [PooledStatefulRedisConnectionProvider] **instance:**
 *    - If the [Annotation] is non-null, it will be used to qualify the binding.
 *    - The [RedisConnectionPoolConfig] is used to configure the underlying connection pool.
 *    - The provided [clientName] will be used to set the connection's client name.
 *    - The [RedisNodeConfig], [RedisCodec], and [useSsl] will be used to configure the
 *      connection's provider function.
 *     - For [PooledConnectionProvider], the pool is registered as a provider for the ConnectionPool Metrics
 *
 * 2. **Adds a binding for the** [StatefulRedisConnectionProvider] **to a multibinder set**.
 *   - Provides access to all [StatefulRedisConnectionProvider]s for lifecycle management.
 *
 * The provider supports both exclusive and shared connections:
 * - Exclusive connections are acquired from the pool and returned when closed
 * - A single shared connection is maintained for non-exclusive operations
 */
internal class StatefulRedisConnectionProviderModule<K : Any, V : Any, T : StatefulRedisConnectionProvider<K, V>>(
  private val connectionProviderType: TypeLiteral<T>,
  private val replicationGroupId: String,
  private val clientName: String,
  private val connectionPoolConfig: RedisConnectionPoolConfig,
  private val nodeConfig: RedisNodeConfig,
  private val codec: RedisCodec<K, V>,
  private val useSsl: Boolean,
  private val password: String?,
  private val annotation: Annotation?,
) : KAbstractModule() {

  override fun configure() {
    val clientKey = keyOf<RedisClient>(annotation)
    val redisClientProvider = getProvider(clientKey)
    val clientMetricsProvider = getProvider(RedisClientMetrics::class.java)

    val redisUri = redisUri {
      with(nodeConfig) {
        withHost(hostname)
        withPort(port)
      }
      withSsl(useSsl)
      password?.let { withPassword(password.toCharArray()) }
    }

    val connectionProviderKey = connectionProviderType.toKey(annotation)

    bind(connectionProviderKey).toProvider {
      @Suppress("UNCHECKED_CAST")
      PooledStatefulRedisConnectionProvider(
        AsyncConnectionPoolSupport.createBoundedObjectPoolAsync(
          object : Supplier<CompletionStage<StatefulRedisConnection<K, V>>> {
            val count = AtomicInteger(0)
            override fun get(): ConnectionFuture<StatefulRedisConnection<K, V>> {
              val suffix = count.incrementAndGet().toString().padStart(5, '0')
              return redisClientProvider.get().connectAsync(codec, redisUri)
                .maybeSetClientName(suffix)
            }
          },
          connectionPoolConfig.toBoundedPoolConfig(),
          false, // this is handled directly in the connection provider
        ).thenApply {
          clientMetricsProvider.get()
            .registerConnectionPoolMetrics(clientName, replicationGroupId, it)
          it
        }.toCompletableFuture(),
        replicationGroupId,
      ) as T
    }.asSingleton()

    Multibinder.newSetBinder(binder(), connectionProviderTypeLiteral).addBinding()
      .to(connectionProviderKey)
  }

  /**
   * Composer for a [StatefulRedisConnection] [CompletableFuture] that will set the client name
   * once the connection is established and return the original [CompletableFuture]
   */
  @Suppress("UNCHECKED_CAST")
  private fun <K, V> ConnectionFuture<StatefulRedisConnection<K, V>>.maybeSetClientName(suffix: String? = null):
    ConnectionFuture<StatefulRedisConnection<K, V>> =
    ("$replicationGroupId:$clientName${suffix?.let { ":it" } ?: ""}" as? K)?.let {
      thenCompose { connection ->
        connection.async().clientSetname(it).thenApply { connection }
      }
    } ?: this
}



