@file:Suppress("unused")

package misk.redis.lettuce.cluster

import io.lettuce.core.RedisURI
import io.lettuce.core.SocketOptions
import io.lettuce.core.SslOptions
import io.lettuce.core.TimeoutOptions
import io.lettuce.core.cluster.ClusterClientOptions
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.resource.ClientResources
import io.lettuce.core.resource.DefaultClientResources
import kotlinx.coroutines.future.await
import misk.redis.lettuce.suspendingUse

/**
 * Creates a new [RedisClusterClient] with the specified configuration.
 *
 * This is a convenience wrapper around [RedisClusterClient.create] that supports
 * cluster-specific configuration options. Unlike standalone Redis, a cluster client
 * always requires a URI for topology discovery.
 *
 * Example:
 * ```kotlin
 * val client = redisClusterClient(
 *   redisUri {
 *     withHost("redis-cluster.example.com")
 *     withPort(6379)
 *     withPassword("secret")
 *   },
 *   clientOptions = clusterClientOptions {
 *     topologyRefreshOptions {
 *       enableAllAdaptiveRefreshTriggers()
 *       enablePeriodicRefresh()
 *     }
 *     socketOptions {
 *       connectTimeout(Duration.ofSeconds(5))
 *     }
 *   }
 * )
 * ```
 *
 * @param redisURI Redis connection URI for cluster discovery
 * @param clientResources Client resources configuration (defaults to [DefaultClientResources.create])
 * @param clientOptions Cluster-specific client options (defaults to [ClusterClientOptions.create])
 * @return Configured [RedisClusterClient] instance
 */
fun redisClusterClient(
  redisURI: RedisURI,
  clientResources: ClientResources = DefaultClientResources.create(),
  clientOptions: ClusterClientOptions = ClusterClientOptions.create()
): RedisClusterClient =
  RedisClusterClient.create(clientResources, redisURI)
    .apply {
      setOptions(clientOptions)
    }

/**
 * Executes a block with a Redis Cluster connection and automatically closes it afterward.
 *
 * This suspending function creates a cluster connection using [RedisClusterClient.connectAsync],
 * executes the provided block, and ensures the connection is closed properly using
 * Kotlin's [use] function. It supports custom key and value types through the codec.
 *
 * Example:
 * ```kotlin
 * client.withConnection(JsonCodec()) {
 *   // Commands are automatically routed to correct nodes
 *   set("user:123", userJson)
 *   
 *   // Cluster-specific operations
 *   val topology = clusterNodes()
 *   val slot = clusterKeyslot("user:123")
 * }
 * ```
 *
 * @param K The type of keys in Redis operations
 * @param V The type of values in Redis operations
 * @param T The return type of the block
 * @param codec The codec for serializing keys and values
 * @param block The code to execute with the connection
 * @return The result of the block execution
 */
suspend inline fun <K, V, T> RedisClusterClient.withConnection(
  codec: RedisCodec<K, V>,
  block: StatefulRedisClusterConnection<K, V>.() -> T
): T = connectAsync(codec).await().suspendingUse(block)

/**
 * Executes a block with a Redis Cluster connection using UTF-8 String codec.
 *
 * This is a convenience wrapper around [withConnection] that uses [StringCodec.UTF8]
 * for both keys and values. It delegates to [RedisClusterClient.connectAsync] with
 * the UTF-8 String codec.
 *
 * Example:
 * ```kotlin
 * client.withConnection {
 *   // Multi-key operations with slot checking
 *   val keys = listOf("user:1", "user:2")
 *   if (keys.map { clusterKeyslot(it) }.distinct().size == 1) {
 *     mget(*keys.toTypedArray())
 *   } else {
 *     keys.map { get(it) }
 *   }
 * }
 * ```
 *
 * @param T The return type of the block
 * @param block The code to execute with the connection
 * @return The result of the block execution
 */
suspend inline fun <T> RedisClusterClient.withConnection(
  block: StatefulRedisClusterConnection<String, String>.() -> T
): T = withConnection(StringCodec.UTF8, block)

/**
 * Executes a block with a blocking Redis Cluster connection.
 *
 * This function uses [RedisClusterClient.connect] for synchronous connection creation.
 * It supports custom key and value types through the codec and handles proper
 * connection cleanup using Kotlin's [use] function.
 *
 * Example:
 * ```kotlin
 * client.withBlockingConnection(protobufCodec) {
 *   // Cluster-aware operations
 *   val nodes = clusterNodes()
 *   nodes.forEach { node ->
 *     val keys = clusterGetKeysInSlot(node.slot, 100)
 *     keys.forEach { key ->
 *       val value = get(key)
 *       // Process value...
 *     }
 *   }
 * }
 * ```
 *
 * @param K The type of keys in Redis operations
 * @param V The type of values in Redis operations
 * @param T The return type of the block
 * @param codec The codec for serializing keys and values
 * @param block The code to execute with the connection
 * @return The result of the block execution
 */
inline fun <K, V, T> RedisClusterClient.withConnectionBlocking(
  codec: RedisCodec<K, V>,
  block: StatefulRedisClusterConnection<K, V>.() -> T
): T = connect(codec).use(block)

/**
 * Executes a block with a blocking Redis Cluster connection using UTF-8 String codec.
 *
 * This is a convenience wrapper around [withConnectionBlocking] that uses
 * [StringCodec.UTF8] for both keys and values. It delegates to [RedisClusterClient.connect]
 * with the UTF-8 String codec.
 *
 * @param T The return type of the block
 * @param block The code to execute with the connection
 * @return The result of the block execution
 */
inline fun <T> RedisClusterClient.withConnectionBlocking(
  block: StatefulRedisClusterConnection<String, String>.() -> T
): T = connect().use(block)

/**
 * Creates [ClusterClientOptions] using a builder pattern.
 *
 * This function provides a Kotlin-friendly wrapper around [ClusterClientOptions.builder].
 * It allows configuration of cluster-specific client options using a more idiomatic
 * builder syntax.
 *
 * Example:
 * ```kotlin
 * val options = clusterClientOptions {
 *   // Configure topology refresh
 *   topologyRefreshOptions {
 *     enableAllAdaptiveRefreshTriggers()
 *     enablePeriodicRefresh(Duration.ofMinutes(1))
 *   }
 *   
 *   // Configure timeouts
 *   timeoutOptions {
 *     timeoutCommands(true)
 *     fixedTimeout(Duration.ofSeconds(5))
 *   }
 *   
 *   // Configure SSL
 *   sslOptions {
 *     keystore(myKeyStore)
 *     truststore(myTrustStore)
 *   }
 * }
 * ```
 *
 * @param builder Lambda with receiver for configuring the [ClusterClientOptions.Builder]
 * @return Configured [ClusterClientOptions] instance
 */
inline fun clusterClientOptions(
  builder: ClusterClientOptions.Builder.() -> Unit
): ClusterClientOptions =
  ClusterClientOptions.builder().apply(builder).build()

/**
 * Creates [SocketOptions] using a builder pattern.
 *
 * This function provides a Kotlin-friendly wrapper around [SocketOptions.builder].
 * It allows configuration of socket options using a more idiomatic builder syntax.
 *
 * @param builder Lambda with receiver for configuring the [SocketOptions.Builder]
 * @return Configured [SocketOptions] instance
 */
inline fun socketOptions(
  builder: SocketOptions.Builder.() -> Unit
): SocketOptions =
  SocketOptions.builder().apply(builder).build()

/**
 * Configures socket options for a [ClusterClientOptions.Builder].
 *
 * This extension function provides a Kotlin-friendly way to configure socket options
 * within a cluster client options builder. It delegates to [ClusterClientOptions.Builder.socketOptions].
 *
 * @param builder Lambda with receiver for configuring the [SocketOptions.Builder]
 * @return The [ClusterClientOptions.Builder] for method chaining
 */
inline fun ClusterClientOptions.Builder.socketOptions(
  builder: SocketOptions.Builder.() -> Unit
): ClusterClientOptions.Builder =
  this@socketOptions.socketOptions(misk.redis.lettuce.cluster.socketOptions(builder))

/**
 * Creates [SslOptions] using a builder pattern.
 *
 * This function provides a Kotlin-friendly wrapper around [SslOptions.builder].
 * It allows configuration of SSL options using a more idiomatic builder syntax.
 *
 * @param builder Lambda with receiver for configuring the [SslOptions.Builder]
 * @return Configured [SslOptions] instance
 */
inline fun sslOptions(
  builder: SslOptions.Builder.() -> Unit
): SslOptions =
  SslOptions.builder().apply(builder).build()

/**
 * Configures SSL options for a [ClusterClientOptions.Builder].
 *
 * This extension function provides a Kotlin-friendly way to configure SSL options
 * within a cluster client options builder. It delegates to [ClusterClientOptions.Builder.sslOptions].
 *
 * @param builder Lambda with receiver for configuring the [SslOptions.Builder]
 * @return The [ClusterClientOptions.Builder] for method chaining
 */
inline fun ClusterClientOptions.Builder.sslOptions(
  builder: SslOptions.Builder.() -> Unit
): ClusterClientOptions.Builder =
  this@sslOptions.sslOptions(misk.redis.lettuce.cluster.sslOptions(builder))

/**
 * Creates [TimeoutOptions] using a builder pattern.
 *
 * This function provides a Kotlin-friendly wrapper around [TimeoutOptions.builder].
 * It allows configuration of timeout options using a more idiomatic builder syntax.
 *
 * @param builder Lambda with receiver for configuring the [TimeoutOptions.Builder]
 * @return Configured [TimeoutOptions] instance
 */
inline fun timeoutOptions(
  builder: TimeoutOptions.Builder.() -> Unit
): TimeoutOptions =
  TimeoutOptions.builder().apply(builder).build()

/**
 * Configures timeout options for a [ClusterClientOptions.Builder].
 *
 * This extension function provides a Kotlin-friendly way to configure timeout options
 * within a cluster client options builder. It delegates to [ClusterClientOptions.Builder.timeoutOptions].
 *
 * @param builder Lambda with receiver for configuring the [TimeoutOptions.Builder]
 * @return The [ClusterClientOptions.Builder] for method chaining
 */
inline fun ClusterClientOptions.Builder.timeoutOptions(
  builder: TimeoutOptions.Builder.() -> Unit
): ClusterClientOptions.Builder =
  this@timeoutOptions.timeoutOptions(misk.redis.lettuce.cluster.timeoutOptions(builder))

/**
 * Creates [ClusterTopologyRefreshOptions] using a builder pattern.
 *
 * This function provides a Kotlin-friendly wrapper around [ClusterTopologyRefreshOptions.builder].
 * It allows configuration of cluster topology refresh behavior using a more idiomatic
 * builder syntax.
 *
 * Example:
 * ```kotlin
 * val options = clusterTopologyRefreshOptions {
 *   enableAllAdaptiveRefreshTriggers()
 *   enablePeriodicRefresh(Duration.ofMinutes(1))
 *   dynamicRefreshSources(true)
 *   closeStaleConnections(true)
 * }
 * ```
 *
 * @param builder Lambda with receiver for configuring the [ClusterTopologyRefreshOptions.Builder]
 * @return Configured [ClusterTopologyRefreshOptions] instance
 */
inline fun clusterTopologyRefreshOptions(
  builder: ClusterTopologyRefreshOptions.Builder.() -> Unit
): ClusterTopologyRefreshOptions =
  ClusterTopologyRefreshOptions.builder().apply(builder).build()

/**
 * Configures topology refresh options for a [ClusterClientOptions.Builder].
 *
 * This extension function provides a Kotlin-friendly way to configure topology
 * refresh behavior within a cluster client options builder. It delegates to
 * [ClusterClientOptions.Builder.topologyRefreshOptions].
 *
 * Example:
 * ```kotlin
 * val options = clusterClientOptions {
 *   clusterTopologyRefreshOptions {
 *     // Refresh on connection failures
 *     enableAllAdaptiveRefreshTriggers()
 *     
 *     // Also refresh periodically
 *     enablePeriodicRefresh(Duration.ofMinutes(1))
 *     
 *     // Cleanup and optimization
 *     closeStaleConnections(true)
 *     dynamicRefreshSources(true)
 *   }
 * }
 * ```
 *
 * @param builder Lambda with receiver for configuring the [ClusterTopologyRefreshOptions.Builder]
 * @return The [ClusterClientOptions.Builder] for method chaining
 */
inline fun ClusterClientOptions.Builder.clusterTopologyRefreshOptions(
  builder: ClusterTopologyRefreshOptions.Builder.() -> Unit
): ClusterClientOptions.Builder =
  this@clusterTopologyRefreshOptions.topologyRefreshOptions(
    misk.redis.lettuce.cluster.clusterTopologyRefreshOptions(
      builder
    )
  )
