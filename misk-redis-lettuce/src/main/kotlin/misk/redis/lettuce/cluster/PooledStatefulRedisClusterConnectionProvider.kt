package misk.redis.lettuce.cluster

import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.support.BoundedAsyncPool
import misk.redis.lettuce.standalone.PooledStatefulRedisConnection
import misk.redis.lettuce.standalone.SharedStatefulRedisConnection
import java.util.concurrent.CompletableFuture

/**
 * A connection provider that manages Redis connections using a bounded async pool.
 *
 * This provider implements [StatefulRedisClusterConnectionProvider], making it suitable for
 * Redis cluster operations. It uses a [BoundedAsyncPool] to efficiently manage and reuse
 * Redis cluster connections.
 *
 * Key features:
 * - Connection pooling with bounded capacity
 * - Automatic connection management and reuse
 * - Asynchronous connection acquisition and release
 * - Safe connection handling with proper cleanup
 * - Shared connection support for non-exclusive operations
 * - Exclusive connection support for operations requiring isolation
 *
 * Connection Types:
 * 1. Exclusive Connections (exclusive=true):
 *    - Dedicated connection from the pool
 *    - Suitable for transactions (multi/exec)
 *    - Required for blocking commands
 *    - Connection is returned to pool on close
 *
 * 2. Shared Connection (exclusive=false):
 *    - Single shared connection for non-exclusive operations
 *    - More efficient for simple read/write operations
 *    - Not suitable for transactions or blocking commands
 *    - Close operations are no-op (connection managed by provider)
 *
 * The provider wraps returned connections to intercept [StatefulRedisClusterConnection.close] and
 * [StatefulRedisClusterConnection.closeAsync] calls, ensuring connections are properly managed:
 * - For exclusive connections: Released back to the pool
 * - For shared connections: No-op (connection remains active)
 */
internal class PooledStatefulRedisClusterConnectionProvider<K : Any, V : Any>(
  internal val poolFuture: CompletableFuture<BoundedAsyncPool<StatefulRedisClusterConnection<K, V>>>,
  override val replicationGroupId: String
) : StatefulRedisClusterConnectionProvider<K, V> {

  private val sharedConnection: CompletableFuture<StatefulRedisClusterConnection<K, V>> by lazy {
    poolFuture.thenCompose {
      it.acquire().thenApply { connection ->
        SharedStatefulRedisClusterConnection(connection)
      }
    }
  }

  override fun acquireAsync(exclusive:Boolean): CompletableFuture<StatefulRedisClusterConnection<K, V>> =
    if (exclusive) {
      // If an exclusive connection is requested, acquire a new connection from the pool
      poolFuture.thenCompose { pool ->
        pool.acquire().thenApply { connection ->
          PooledStatefulRedisClusterConnection(pool, connection)
        }
      }
    } else {
      // Return the shared connection for non-exclusive requests
      sharedConnection
    }
}

class PooledStatefulRedisClusterConnection<K : Any, V : Any>(
  private val pool: BoundedAsyncPool<StatefulRedisClusterConnection<K, V>>,
  private val connection: StatefulRedisClusterConnection<K, V>,
) : StatefulRedisClusterConnection<K, V> by connection {
  override fun close() {
    closeAsync().get()
  }

  override fun closeAsync(): CompletableFuture<Void> =
    pool.release(connection)
}

class SharedStatefulRedisClusterConnection<K : Any, V : Any>(
  private val connection: StatefulRedisClusterConnection<K, V>,
) : StatefulRedisClusterConnection<K, V> by connection {
  override fun close() {
    closeAsync().get()
  }

  override fun closeAsync(): CompletableFuture<Void> =
    CompletableFuture.completedFuture(null) // NOOP
}


