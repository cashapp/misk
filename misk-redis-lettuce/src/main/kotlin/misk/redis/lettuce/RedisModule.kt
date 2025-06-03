package misk.redis.lettuce

import com.google.inject.multibindings.Multibinder
import io.lettuce.core.AbstractRedisClient
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.redis.lettuce.cluster.RedisClusterModule
import misk.redis.lettuce.standalone.RedisStandaloneModule
import kotlin.reflect.KClass

/**
 * A Misk/Guice module for configuring Redis clients and connection management.
 *
 * This module provides Redis connectivity for both standalone and cluster configurations,
 * supporting different key-value type combinations and custom codecs. It automatically
 * handles connection management, pooling, and lifecycle based on the provided configuration.
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
 */
class RedisModule<K : Any, V : Any> internal constructor(
  private val keyType: KClass<K>,
  private val valueType: KClass<V>,
  private val config: AbstractRedisConfig,
  private val codec: RedisCodec<K, V>,
) : KAbstractModule() {

  override fun configure() {
    Multibinder.newSetBinder(binder(), connectionProviderTypeLiteral)
    newMultibinder<AbstractRedisClient>()
    newMultibinder<FunctionCodeLoader>()

    when (config) {
      is RedisConfig ->
        install(
          RedisStandaloneModule(
            config = config,
            keyType = keyType,
            valueType = valueType,
            codec = codec,
          )
        )

      is RedisClusterConfig ->
        install(
          RedisClusterModule(
            config = config,
            keyType = keyType,
            valueType = valueType,
            codec = codec,
          )
        )
    }
    install(ServiceModule<RedisService>().enhancedBy<ReadyService>())
  }

  companion object {
    /**
     * Creates a [RedisModule] with explicit key and value types.
     *
     * This factory method allows full control over the Redis configuration, including
     * key/value types and the codec used for serialization.
     */
    fun <K : Any, V : Any> create(
      keyType: KClass<K>,
      valueType: KClass<V>,
      config: AbstractRedisConfig,
      codec: RedisCodec<K, V>,
    ) = RedisModule(keyType, valueType, config, codec)

    /**
     * Creates a [RedisModule] with reified type parameters.
     *
     * This factory method uses Kotlin's reified generics to automatically determine
     * the key and value types, while still allowing custom codec configuration.
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
    ) = create(K::class, V::class, config, codec)

    /**
     * Creates a [RedisModule] with default String codec.
     *
     * This is a convenience factory method that creates a module using UTF-8 String
     * codec for both keys and values. It's suitable for simple use cases where both
     * keys and values are strings.
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
    fun create(
      config: AbstractRedisConfig,
    ) = create(config, StringCodec.UTF8)
  }
}
