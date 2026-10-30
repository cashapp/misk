package misk.redis.lettuce.cluster

import com.google.inject.name.Names
import io.lettuce.core.AbstractRedisClient
import io.lettuce.core.ClientOptions.DisconnectedBehavior
import io.lettuce.core.RedisClient
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import misk.redis.lettuce.FunctionCodeLoader
import misk.redis.lettuce.NonEssentialRedisClient
import misk.redis.lettuce.RedisClusterConfig
import misk.redis.lettuce.connectionProviderTypeLiteral
import misk.redis.lettuce.metrics.RedisClientMetrics
import misk.redis.lettuce.redisUri
import misk.redis.lettuce.standalone.clientResources
import misk.redis.lettuce.thenComposeUsing
import misk.redis2.metrics.RedisClientMetricsCommandLatencyRecorder

/**
 * Internal module that handles the configuration of a clustered Redis service.
 *
 * This supports various replication groups, each configured with a [RedisClusterConfig] object. It supports any
 * key/value type with a corresponding [RedisCodec].
 *
 * This module does the following:
 * 1. **Creates a binding for a** [RedisClient] **for each replication group.** Each client will be configured using the
 *    corresponding [RedisConfig] object and will install a [RedisClientMetricsCommandLatencyRecorder] to record command
 *    latencies for [RedisClientMetrics].
 * 2. **Creates a binding for a** [StatefulRedisClusterConnectionProvider] **using the** [RedisClusterConfig]'s
 *    `writer_endpoint`. This will either be a [PooledStatefulRedisClusterConnectionProvider] or a
 *    [SharedStatefulRedisClusterConnectionProvider], depending on whether connection pooling is enabled in the
 *    [RedisClusterConfig].
 *
 * ---
 *
 * **Note:** If there is only a single replication group, the bindings will not be qualified. If there are multiple
 * replication groups, bindings will be qualified using [Names.named] with the replication group ID.
 */
internal class RedisClusterModule<K : Any, V : Any>
internal constructor(
  private val config: RedisClusterConfig,
  private val keyType: KClass<K>,
  private val valueType: KClass<V>,
  private val codec: RedisCodec<K, V>,
  private val nonEssential: Boolean,
) : KAbstractModule() {

  override fun configure() {
    config.forEach { replicationGroupId, clusterGroupConfig ->
      // If there is just a single replication group, use no qualifier
      val qualifier = if (config.size == 1) null else Names.named(replicationGroupId)
      val clusterClientKey = keyOf<RedisClusterClient>(qualifier)

      val redisClientMetricsProvider = getProvider(RedisClientMetrics::class.java)

      bind(clusterClientKey)
        .toProvider {
          redisClusterClient(
            with(clusterGroupConfig.configuration_endpoint) {
              redisUri {
                withHost(hostname.takeIf { it.isNotBlank() } ?: "localhost")
                withPort(port)
                val username = clusterGroupConfig.redis_auth_username
                val password = clusterGroupConfig.redis_auth_password.toCharArray()
                if (username != null) {
                  withAuthentication(username, password)
                } else {
                  withPassword(password)
                }
                withSsl(clusterGroupConfig.use_ssl)
              }
            },
            clientOptions =
              clusterClientOptions {
                clusterTopologyRefreshOptions {
                  enablePeriodicRefresh()
                  refreshPeriod(15.seconds.toJavaDuration())
                  enableAllAdaptiveRefreshTriggers()
                }
                socketOptions { connectTimeout(clusterGroupConfig.timeout_ms.milliseconds.toJavaDuration()) }
                if (nonEssential) disconnectedBehavior(DisconnectedBehavior.REJECT_COMMANDS)
              },
            clientResources =
              clientResources {
                commandLatencyRecorder(
                  RedisClientMetricsCommandLatencyRecorder(
                    replicationGroupId = replicationGroupId,
                    clientMetrics = redisClientMetricsProvider.get(),
                  )
                )
              },
          )
        }
        .asSingleton()

      // Add the client binding to the multibind for all clients
      multibind<AbstractRedisClient>().to(clusterClientKey)

      // Add a binding for a function loader if there is configuration
      val clientProvider = getProvider(clusterClientKey)
      val functionCodeLoader =
        clusterGroupConfig.function_code_file_path?.let { codeResourcePath ->
          ClusterFunctionCodeLoader(clientProvider = clientProvider, codeResourcePath = codeResourcePath)
        }
      if (nonEssential) {
        multibind<NonEssentialRedisClient>().toProvider {
          NonEssentialRedisClient(
            replicationGroupId = replicationGroupId,
            client = clientProvider.get(),
            ping = {
              val client = clientProvider.get()
              client
                .refreshPartitionsAsync()
                .thenCompose { client.connectAsync(StringCodec.UTF8) }
                .thenComposeUsing { it.async().ping() }
            },
            functionCodeLoader = functionCodeLoader,
            startupVerified = redisClientMetricsProvider.get().startupVerifiedGauge.labels(replicationGroupId),
          )
        }
      } else if (functionCodeLoader != null) {
        multibind<FunctionCodeLoader>().toInstance(functionCodeLoader)
      }

      with(clusterGroupConfig) {
        install(
          StatefulRedisClusterConnectionProviderModule(
            connectionProviderType =
              connectionProviderTypeLiteral<StatefulRedisClusterConnectionProvider<K, V>>(
                keyType = keyType,
                valueType = valueType,
              ),
            replicationGroupId = replicationGroupId,
            connectionPoolConfig = connection_pool,
            clientName = client_name ?: "cluster",
            codec = codec,
            annotation = qualifier,
          )
        )
      }
    }
  }
}
