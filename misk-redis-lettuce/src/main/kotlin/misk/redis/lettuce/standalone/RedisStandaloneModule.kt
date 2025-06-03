package misk.redis.lettuce.standalone

import com.google.inject.name.Names
import io.lettuce.core.AbstractRedisClient
import io.lettuce.core.RedisClient
import io.lettuce.core.codec.RedisCodec
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import misk.redis.lettuce.RedisConfig
import misk.redis.lettuce.RedisReplicationGroupConfig
import misk.redis.lettuce.connectionProviderTypeLiteral
import misk.redis.lettuce.redisUri
import misk.redis.lettuce.FunctionCodeLoader
import misk.redis.lettuce.metrics.RedisClientMetrics
import misk.redis2.metrics.RedisClientMetricsCommandLatencyRecorder
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

/**
 * Internal module that handles the configuration of a standalone Redis service.
 *
 * This supports various replication groups, each configured with a [RedisConfig] object.
 * It supports any key/value type with a corresponding [RedisCodec].
 *
 * This module does the following:
 *
 * 1. **Creates a binding for a** [RedisClient] **for each replication group.**
 *    Each client will be configured using the corresponding [RedisConfig] object
 *    and will install a [RedisClientMetricsCommandLatencyRecorder] to record
 *    command latencies for [RedisClientMetrics].
 *
 * 2. **Creates a binding for a** [ReadWriteStatefulRedisConnectionProvider] **using the** [RedisConfig]'s
 *    `writer_endpoint`.
 *    This will either be a [PooledStatefulRedisConnectionProvider] or a [SharedStatefulRedisConnectionProvider],
 *    depending on whether connection pooling is enabled in the [RedisConfig].
 *
 * 3. **Creates a binding for a** [ReadOnlyStatefulRedisConnectionProvider] **using the** [RedisConfig]'s
 *    `reader_endpoint`.
 *    If the `reader_endpoint` is omitted, the `writer_endpoint` will be used instead.
 *
 * ---
 *
 * **Note:**
 * If there is only a single replication group, the bindings will not be qualified.
 * If there are multiple replication groups, bindings will be qualified using [Names.named]
 * with the replication group ID.
 */
internal class RedisStandaloneModule<K : Any, V : Any> internal constructor(
  private val config: RedisConfig,
  private val keyType: KClass<K>,
  private val valueType: KClass<V>,
  private val codec: RedisCodec<K, V>,
) : KAbstractModule() {

  override fun configure() {
    // Create a RedisClient for each replication group. If there are more than one,
    // qualify it by the replication group id. If there is only one,
    // for convenience, use no qualifier
    config.forEach { replicationGroupId, replicationGroupConfig: RedisReplicationGroupConfig ->
      // If there is just a single replication group, use no qualifier
      val qualifier = if (config.size == 1) null else Names.named(replicationGroupId)
      val redisClientKey = keyOf<RedisClient>(qualifier)

      val redisClientMetricsProvider = getProvider(RedisClientMetrics::class.java)

      val redisPrimaryUri = redisUri {
        with(replicationGroupConfig.writer_endpoint) {
          withHost(hostname)
          withPort(port)
        }
        withPassword(replicationGroupConfig.redis_auth_password.toCharArray())
        withSsl(replicationGroupConfig.use_ssl)
      }

      bind(redisClientKey).toProvider {
        redisClient(
          // Use the Primary as the default connection endpoint
          redisURI = redisPrimaryUri,
          clientOptions = clientOptions {
            socketOptions {
              connectTimeout(replicationGroupConfig.timeout_ms.milliseconds.toJavaDuration())
            }
          },
          clientResources = clientResources {
            commandLatencyRecorder(
              RedisClientMetricsCommandLatencyRecorder(
                replicationGroupId = replicationGroupId,
                clientMetrics = redisClientMetricsProvider.get(),
              ),
            )
          },
        )
      }.asSingleton()

      // Add the client binding to the multibind for all clients
      multibind<AbstractRedisClient>().to(redisClientKey)

      // Add a binding for a function loader if there is configuration
      val clientProvider = getProvider(redisClientKey)
      replicationGroupConfig.function_code_file_path?.also { codeResourcePath ->
        multibind<FunctionCodeLoader>().toProvider {
          StandaloneFunctionCodeLoader(
            clientProvider = clientProvider,
            uri = redisPrimaryUri,
            codeResourcePath = codeResourcePath,
          )
        }
      }

      with(replicationGroupConfig) {
        install(
          StatefulRedisConnectionProviderModule(
            connectionProviderType =
              connectionProviderTypeLiteral<ReadWriteStatefulRedisConnectionProvider<K, V>>(
                keyType = keyType,
                valueType = valueType,
              ),
            replicationGroupId = replicationGroupId,
            clientName = client_name?.let { "$it:readWrite" } ?: "readWrite",
            connectionPoolConfig = connection_pool,
            nodeConfig = writer_endpoint,
            codec = codec,
            useSsl = use_ssl,
            password = redis_auth_password.takeIf { it.isNotBlank() },
            annotation = qualifier,
          ),
        )
        install(
          StatefulRedisConnectionProviderModule(
            connectionProviderType =
              connectionProviderTypeLiteral<ReadOnlyStatefulRedisConnectionProvider<K, V>>(
                keyType = keyType,
                valueType = valueType,
              ),
            replicationGroupId = replicationGroupId,
            clientName = client_name?.let { "$it:readOnly" } ?: "readOnly",
            connectionPoolConfig = connection_pool,
            nodeConfig = reader_endpoint ?: writer_endpoint,
            codec = codec,
            useSsl = use_ssl,
            password = redis_auth_password.takeIf { it.isNotBlank() },
            annotation = qualifier,
          ),
        )
      }
    }
  }
}

