package misk.redis.lettuce.cluster

import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.coroutines
import io.lettuce.core.cluster.api.coroutines.RedisClusterCoroutinesCommands
import io.lettuce.core.cluster.api.sync.RedisClusterCommands
import misk.redis.lettuce.ConnectionProvider
import misk.redis.lettuce.suspendingUse

/**
 * A specialized [ConnectionProvider] for [StatefulRedisClusterConnection]s.
 */
interface StatefulRedisClusterConnectionProvider<K : Any, V : Any> :
  ConnectionProvider<K, V, StatefulRedisClusterConnection<K, V>>

/**
 * Type alias for a string-based cluster connection provider.
 *
 * This is a convenience type for the common case of using string keys and values
 * with a Redis Cluster connection. It's particularly useful when working with
 * simple string-based data across a cluster.
 */
typealias ClusterConnectionProvider = StatefulRedisClusterConnectionProvider<String, String>

/**
 * Executes a block of code with coroutine-based Redis Cluster commands.
 *
 * This suspending helper function:
 * 1. Acquires a cluster connection
 * 2. Provides access to [RedisClusterCoroutinesCommands]
 * 3. Automatically closes the connection after use
 * 4. Properly handles exceptions during execution and cleanup
 *
 * Example usage:
 * ```kotlin
 * val result = provider.withCoroutineCommands {
 *   // Regular commands are routed automatically
 *   set("key", "value")
 *
 *   // Cluster-specific operations
 *   val slot = clusterKeyslot("key")
 *   val node = clusterGetNodeBySlot(slot)
 *   val keys = clusterGetKeysInSlot(slot, 10)
 *   keys
 * }
 * ```
 */
suspend inline fun <K : Any, V : Any, T> StatefulRedisClusterConnectionProvider<K, V>.withConnection(
  exclusive: Boolean = false,
  block: RedisClusterCoroutinesCommands<K, V>.() -> T
): T = acquire(exclusive).suspendingUse { block(it.coroutines()) }

/**
 * Executes a block of code with synchronous Redis Cluster commands.
 *
 * This helper function:
 * 1. Acquires a cluster connection
 * 2. Provides access to [RedisClusterCommands]
 * 3. Automatically closes the connection after use
 * 4. Uses Kotlin's [use] function for resource management
 *
 * Example usage:
 * ```kotlin
 * val result = provider.withBlockingConnection {
 *   // Multi-key operations with slot checking
 *   val keys = listOf("user:1", "user:2")
 *   if (keys.map { clusterKeyslot(it) }.distinct().size == 1) {
 *     // Keys in same slot, can use multi-key operation
 *     mget(*keys.toTypedArray())
 *   } else {
 *     // Keys in different slots, get individually
 *     keys.map { get(it) }
 *   }
 * }
 * ```
 */
inline fun <K : Any, V : Any, T> StatefulRedisClusterConnectionProvider<K, V>.withConnectionBlocking(
  exclusive: Boolean = false,
  block: RedisClusterCommands<K, V>.() -> T
): T = acquireBlocking(exclusive).use { connection -> block(connection.sync()) }
