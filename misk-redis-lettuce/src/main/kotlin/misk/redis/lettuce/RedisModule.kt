package misk.redis.lettuce

import com.google.inject.multibindings.Multibinder
import io.lettuce.core.AbstractRedisClient
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import kotlin.reflect.KClass
import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.metrics.v2.Metrics
import misk.redis.lettuce.cluster.RedisClusterModule
import misk.redis.lettuce.standalone.RedisStandaloneModule

/**
 * A Misk/Guice module for configuring Redis clients and connection management.
 *
 * This module provides Redis connectivity for both standalone and cluster configurations, supporting different
 * key-value type combinations and custom codecs. It automatically handles connection management, pooling, and lifecycle
 * based on the provided configuration.
 *
 * Key features:
 * - Support for both standalone and cluster Redis configurations
 * - Type-safe Redis operations with customizable key and value types
 * - Flexible codec configuration for data serialization
 * - Automatic connection management and/or pooling
 * - SSL/TLS support configured via the Redis configuration
 *
 * Example using default String codec in a standalone configuration:
 * ```kotlin
 * // Configure Redis with default UTF-8 String codec
 * val config = RedisConfig(
 *   "misk-exemplar-001" to ReplicationGroupConfig(
 *     primary_endpoint = NodeConfig(
 *       hostname = "redis001.example.com",
 *       port = 6379
 *     ),
 *     reader_endpoint = NodeConfig(
 *       hostname = "redis002.example.com",
 *       port = 6379
 *     ),
 *     redis_auth_password = "secret",
 *     use_ssl = true,
 *     timeout_ms = 2000
 *   )
 * )
 *
 * // Create and install the module
 * install(RedisModule.create(config))
 * ```
 *
 * Example using default String codec in a cluster configuration:
 * ```kotlin
 * // Configure Redis Cluster with default UTF-8 String codec
 * val config = RedisClusterConfig(
 *   "misk-cluster-001" to RedisClusterGroupConfig(
 *     configuration_endpoint = NodeConfig(
 *       hostname = "redis-cluster.example.com",
 *       port = 6379
 *     ),
 *     redis_auth_password = "secret",
 *     use_ssl = true,
 *     timeout_ms = 2000,
 *     connectionPoolConfig = RedisConnectionPoolConfig(
 *       maxTotal = 32,        // Higher pool size for cluster
 *       maxIdle = 8,
 *       minIdle = 2
 *     )
 *   )
 * )
 *
 * // Create and install the module
 * install(RedisModule.create(config))
 * ```
 *
 * Example with custom types and codec:
 * ```kotlin
 * // Custom codec for JSON serialization
 * class JsonCodec : RedisCodec<String, JsonNode> {
 *   private val stringCodec = StringCodec.UTF8
 *   private val mapper = ObjectMapper()
 *
 *   override fun decodeKey(bytes: ByteBuffer): String =
 *     stringCodec.decodeKey(bytes)
 *
 *   override fun decodeValue(bytes: ByteBuffer): JsonNode =
 *     mapper.readTree(bytes.array())
 *
 *   override fun encodeKey(key: String): ByteBuffer =
 *     stringCodec.encodeKey(key)
 *
 *   override fun encodeValue(value: JsonNode): ByteBuffer =
 *     ByteBuffer.wrap(mapper.writeValueAsBytes(value))
 * }
 *
 * // Create module with custom types
 * val redisModule = RedisModule.create<String, JsonNode>(
 *   config = config,
 *   codec = JsonCodec()
 * )
 * install(redisModule)
 * ```
 *
 * ## Non-essential Redis
 *
 * By default Redis is essential: the app doesn't start until every configured Redis is reachable. Set `nonEssential`
 * when the app can serve without Redis, for example when Redis is only a cache:
 * ```kotlin
 * install(RedisModule.create(config, nonEssential = true))
 * ```
 *
 * In this mode:
 * - [RedisService] doesn't block startup or fail if Redis is unreachable. It verifies connectivity in the background,
 *   retrying until Redis is reachable, then loads any configured function code, retrying that too until it succeeds or
 *   Redis rejects the code. The `redis_client_startup_verified` gauge reports 1 per replication group once it has
 *   connected.
 * - Commands are rejected immediately while a connection is disconnected, instead of queueing until Redis reconnects or
 *   the command times out. Callers should treat these failures like a cache miss.
 *
 * In either mode, a [ConnectionProvider] that couldn't connect retries on later acquires (with backoff), so it recovers
 * once Redis is reachable.
 */
class RedisModule<K : Any, V : Any>
internal constructor(
  private val keyType: KClass<K>,
  private val valueType: KClass<V>,
  private val config: AbstractRedisConfig,
  private val codec: RedisCodec<K, V>,
  private val nonEssential: Boolean,
) : KAbstractModule() {

  override fun configure() {
    Multibinder.newSetBinder(binder(), connectionProviderTypeLiteral)
    newMultibinder<AbstractRedisClient>()
    newMultibinder<FunctionCodeLoader>()
    newMultibinder<NonEssentialRedisClient>()
    requireBinding<Metrics>()

    when (config) {
      is RedisConfig ->
        install(
          RedisStandaloneModule(
            config = config,
            keyType = keyType,
            valueType = valueType,
            codec = codec,
            nonEssential = nonEssential,
          )
        )

      is RedisClusterConfig ->
        install(
          RedisClusterModule(
            config = config,
            keyType = keyType,
            valueType = valueType,
            codec = codec,
            nonEssential = nonEssential,
          )
        )
    }
    install(ServiceModule<RedisService>().enhancedBy<ReadyService>())
  }

  companion object {
    /**
     * Creates a [RedisModule] with explicit key and value types.
     *
     * This factory method allows full control over the Redis configuration, including key/value types and the codec
     * used for serialization.
     *
     * @param nonEssential see [RedisModule]
     */
    @JvmOverloads
    fun <K : Any, V : Any> create(
      keyType: KClass<K>,
      valueType: KClass<V>,
      config: AbstractRedisConfig,
      codec: RedisCodec<K, V>,
      nonEssential: Boolean = false,
    ) = RedisModule(keyType, valueType, config, codec, nonEssential)

    /**
     * Creates a [RedisModule] with reified type parameters.
     *
     * This factory method uses Kotlin's reified generics to automatically determine the key and value types, while
     * still allowing custom codec configuration.
     *
     * Example:
     * ```kotlin
     * val module = RedisModule.create<String, JsonNode>(
     *   config = redisConfig,
     *   codec = JsonCodec()
     * )
     * ```
     */
    @Suppress("SameParameterValue")
    inline fun <reified K : Any, reified V : Any> create(
      config: AbstractRedisConfig,
      codec: RedisCodec<K, V>,
      nonEssential: Boolean = false,
    ) = create(K::class, V::class, config, codec, nonEssential)

    /**
     * Creates a [RedisModule] with default String codec.
     *
     * This is a convenience factory method that creates a module using UTF-8 String codec for both keys and values.
     * It's suitable for simple use cases where both keys and values are strings.
     *
     * Example:
     * ```kotlin
     * val module = RedisModule.create(
     *   config = RedisConfig(
     *     "primary" to ReplicationGroupConfig(
     *       primary_endpoint = NodeConfig("localhost", 6379),
     *       redis_auth_password = "secret",
     *       use_ssl = true
     *     )
     *   )
     * )
     * ```
     */
    @JvmOverloads
    fun create(config: AbstractRedisConfig, nonEssential: Boolean = false) =
      create(config, StringCodec.UTF8, nonEssential)
  }
}
