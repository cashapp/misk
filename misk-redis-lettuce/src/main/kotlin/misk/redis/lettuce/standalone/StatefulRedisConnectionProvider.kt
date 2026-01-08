@file:Suppress("unused")

package misk.redis.lettuce.standalone

import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.lettuce.core.api.sync.RedisCommands
import misk.redis.lettuce.ConnectionProvider
import misk.redis.lettuce.suspendingUse

/** A specialized [ConnectionProvider] for [StatefulRedisConnection]s. */
interface StatefulRedisConnectionProvider<K : Any, V : Any> : ConnectionProvider<K, V, StatefulRedisConnection<K, V>>

/**
 * Type alias for a string-based read/write connection provider.
 *
 * This is a convenience type for the common case of using string keys and values with a read/write connection. It's
 * particularly useful when connecting to a single Redis server.
 */
typealias StringConnectionProvider = ReadWriteConnectionProvider

/**
 * Interface for read/write Redis connection providers.
 *
 * This specialized provider is used for connections that support both read and write operations. In a replicated
 * environment (like AWS ElastiCache), this typically represents connections to the primary node.
 */
interface ReadWriteStatefulRedisConnectionProvider<K : Any, V : Any> : StatefulRedisConnectionProvider<K, V>

/**
 * Type alias for a string-based read/write connection provider. This is a convenience type for the common case of using
 * string keys and values with a Redis connection.
 */
typealias ReadWriteConnectionProvider = ReadWriteStatefulRedisConnectionProvider<String, String>

/**
 * Interface for read-only Redis connection providers.
 *
 * This specialized provider is used for connections that only need read access. In a replicated environment (like AWS
 * ElastiCache), this typically represents connections to replica nodes.
 */
interface ReadOnlyStatefulRedisConnectionProvider<K : Any, V : Any> : StatefulRedisConnectionProvider<K, V>

/**
 * Type alias for a string-based read-only connection provider. This is a convenience type for the common case of using
 * string keys and values with a Redis connection.
 */
typealias ReadOnlyConnectionProvider = ReadOnlyStatefulRedisConnectionProvider<String, String>

/**
 * Executes a block of code with coroutine-based Redis commands.
 *
 * This suspending helper function:
 * 1. Acquires a connection
 * 2. Provides access to [RedisCoroutinesCommands]
 * 3. Automatically closes the connection after use
 * 4. Properly handles exceptions during execution and cleanup
 *
 * Example usage:
 * ```kotlin
 * val result = provider.withCoroutineCommands {
 *   get("key") // Suspending call
 * }
 * ```
 */
suspend inline fun <K : Any, V : Any, T> StatefulRedisConnectionProvider<K, V>.withConnection(
  exclusive: Boolean = false,
  block: RedisCoroutinesCommands<K, V>.() -> T,
): T = acquire(exclusive).suspendingUse { block(it.coroutines()) }

/**
 * Executes a block of code with synchronous Redis commands.
 *
 * This helper function:
 * 1. Acquires a connection
 * 2. Provides access to [RedisCommands]
 * 3. Automatically closes the connection after use
 * 4. Properly handles exceptions during execution and cleanup
 *
 * Example usage:
 * ```kotlin
 * val result = provider.withBlockingConnection {
 *   get("key") // Blocking call
 * }
 * ```
 */
inline fun <K : Any, V : Any, T> StatefulRedisConnectionProvider<K, V>.withConnectionBlocking(
  exclusive: Boolean = false,
  block: RedisCommands<K, V>.() -> T,
): T = acquireBlocking(exclusive).use { block(it.sync()) }
