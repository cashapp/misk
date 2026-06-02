package misk.redis.lettuce.metrics

import io.lettuce.core.support.BoundedAsyncPool
import io.prometheus.client.Histogram
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.time.Duration
import misk.metrics.v2.Metrics
import misk.metrics.v2.ProvidedGauge

/**
 * Metrics collector for Redis client operations and connection pool statistics.
 *
 * This class provides comprehensive monitoring of Redis client behavior through two main types of metrics:
 * 1. Connection Pool Metrics (Gauges):
 *     - Max total connections
 *     - Max/min idle connections
 *     - Active connections
 *     - Current idle connections
 * 2. Operation Latency Metrics (Histograms):
 *     - First response time
 *     - Total operation time
 *
 * All metrics are labeled with relevant context information to enable detailed analysis:
 * - `name`: Client or pool identifier
 * - `replication_group_id`: Redis replication group identifier
 * - `command`: Redis command type (for latency metrics)
 * - `remote_address`: Redis server address
 * - `local_address`: Client address
 *
 * Example metric names:
 * ```
 * redis_client_max_total_connections
 * redis_client_active_connections
 * redis_client_first_response_time_millis
 * redis_client_operation_time_millis
 * ```
 *
 * Usage example:
 * ```kotlin
 * @Inject
 * fun myService(metrics: RedisClientMetrics) {
 *   // Register connection pool metrics
 *   metrics.registerConnectionPoolMetrics(
 *     name = "my-service",
 *     replicationGroupId = "primary",
 *     pool = myConnectionPool
 *   )
 *
 *   // Record operation latencies
 *   metrics.recordOperationTime(
 *     replicationGroupId = "primary",
 *     commandType = "GET",
 *     remoteAddress = "redis.example.com:6379",
 *     localAddress = "10.0.0.1:12345",
 *     value = 100.milliseconds
 *   )
 * }
 * ```
 */
@Singleton
internal class RedisClientMetrics @Inject constructor(metrics: Metrics) {

  /**
   * Registers connection pool metrics for a Redis client instance.
   *
   * This method sets up gauge metrics to monitor the state of a [BoundedAsyncPool]. The metrics are collected on-demand
   * when scraped by the metrics system.
   *
   * Registered metrics:
   * - `redis_client_max_total_connections`: Maximum allowed connections
   * - `redis_client_max_idle_connections`: Maximum allowed idle connections
   * - `redis_client_min_idle_connections`: Minimum maintained idle connections
   * - `redis_client_active_connections`: Current active (in-use) connections
   * - `redis_client_idle_connections`: Current idle connections
   *
   * Each metric is labeled with:
   * - `name`: Identifier for the client instance
   * - `replication_group_id`: Redis replication group identifier
   *
   * @param name Client instance identifier
   * @param replicationGroupId Redis replication group identifier
   * @param pool The connection pool to monitor
   */
  fun registerConnectionPoolMetrics(name: String, replicationGroupId: String, pool: BoundedAsyncPool<*>) {
    maxTotalConnectionsGauge.labels(name, replicationGroupId).registerProvider(pool) { maxTotal }
    maxIdleConnectionsGauge.labels(name, replicationGroupId).registerProvider(pool) { maxIdle }
    minIdleConnectionsGauge.labels(name, replicationGroupId).registerProvider(pool) { minIdle }
    activeConnectionsGauge.labels(name, replicationGroupId).registerProvider(pool) { objectCount - idle }
    idleConnectionsGauge.labels(name, replicationGroupId).registerProvider(pool) { idle }
  }

  /**
   * Records the latency between request initiation and first response byte.
   *
   * This metric helps track initial response time for Redis operations, which is useful for monitoring Redis server
   * responsiveness and network latency.
   *
   * The metric is recorded as a histogram with name `redis_client_first_response_time_millis` and includes the
   * following labels:
   * - `replication_group_id`: Redis replication group identifier
   * - `command`: Redis command type (e.g., GET, SET)
   * - `remote_address`: Redis server address
   * - `local_address`: Client address
   *
   * @param replicationGroupId Redis replication group identifier
   * @param commandType The Redis command being executed
   * @param value The measured duration
   */
  fun recordFirstResponseTime(replicationGroupId: String, commandType: String, value: Duration) {
    firstResponseTime.labels(replicationGroupId, commandType).observe(value.inWholeMilliseconds.toDouble())
  }

  /**
   * Records the total latency for a Redis operation.
   *
   * This metric tracks the complete duration of Redis operations from initiation to completion, including command
   * execution and response processing time.
   *
   * The metric is recorded as a histogram with name `redis_client_operation_time_millis` and includes the following
   * labels:
   * - `replication_group_id`: Redis replication group identifier
   * - `command`: Redis command type (e.g., GET, SET)
   * - `remote_address`: Redis server address
   * - `local_address`: Client address
   *
   * @param replicationGroupId Redis replication group identifier
   * @param commandType The Redis command being executed
   * @param value The measured duration
   */
  fun recordOperationTime(replicationGroupId: String, commandType: String, value: Duration) {
    operationTime.labels(replicationGroupId, commandType).observe(value.inWholeMilliseconds.toDouble())
  }

  internal val maxTotalConnectionsGauge: ProvidedGauge =
    metrics.providedGauge(
      name = MAX_TOTAL_CONNECTIONS,
      help =
        """
        Max number of connections for the misk-redis2 client connection pool.
        This is configured on app startup.
        """
          .trimIndent(),
      labelNames = listOf(NAME_LABEL, REPLICATION_GROUP_ID_LABEL),
    )

  internal val maxIdleConnectionsGauge: ProvidedGauge =
    metrics.providedGauge(
      name = MAX_IDLE_CONNECTIONS,
      help =
        """
        Max number of idle connections for the misk-redis2 client connection pool.
        This is configured on app startup.
        """
          .trimIndent(),
      labelNames = listOf(NAME_LABEL, REPLICATION_GROUP_ID_LABEL),
    )

  internal val minIdleConnectionsGauge: ProvidedGauge =
    metrics.providedGauge(
      name = MIN_IDLE_CONNECTIONS,
      help =
        """
        Min number of idle connections for the misk-redis2 client connection pool.
        This is configured on app startup.
        """
          .trimIndent(),
      labelNames = listOf(NAME_LABEL, REPLICATION_GROUP_ID_LABEL),
    )

  internal val activeConnectionsGauge: ProvidedGauge =
    metrics.providedGauge(
      name = ACTIVE_CONNECTIONS,
      help = "Current number of active connections for the misk-redis2 client connection pool.",
      labelNames = listOf(NAME_LABEL, REPLICATION_GROUP_ID_LABEL),
    )

  internal val idleConnectionsGauge: ProvidedGauge =
    metrics.providedGauge(
      name = IDLE_CONNECTIONS,
      help = "Current number of idle connections for the misk-redis2 client connection pool.",
      labelNames = listOf(NAME_LABEL, REPLICATION_GROUP_ID_LABEL),
    )

  internal val firstResponseTime: Histogram =
    metrics.histogram(
      name = FIRST_RESPONSE_TIME,
      help = "The time it took in milliseconds, as reported by the client, to get a first response from an operation.",
      labelNames = listOf(REPLICATION_GROUP_ID_LABEL, COMMAND_LABEL),
    )

  internal val operationTime: Histogram =
    metrics.histogram(
      name = OPERATION_TIME,
      help = "The time it took in milliseconds, as reported by the client, to complete an operation.",
      labelNames = listOf(REPLICATION_GROUP_ID_LABEL, COMMAND_LABEL),
    )

  companion object {
    /** Maximum number of connections allowed in the pool */
    const val MAX_TOTAL_CONNECTIONS = "redis_client_max_total_connections"

    /** Maximum number of idle connections allowed in the pool */
    const val MAX_IDLE_CONNECTIONS = "redis_client_max_idle_connections"

    /** Minimum number of idle connections to maintain in the pool */
    const val MIN_IDLE_CONNECTIONS = "redis_client_min_idle_connections"

    /** Current number of idle connections in the pool */
    const val IDLE_CONNECTIONS = "redis_client_idle_connections"

    /** Current number of active (in-use) connections in the pool */
    const val ACTIVE_CONNECTIONS = "redis_client_active_connections"

    /** Time in milliseconds between request initiation and first response byte */
    const val FIRST_RESPONSE_TIME = "redis_client_first_response_time_millis"

    /** Total time in milliseconds for a Redis operation to complete */
    const val OPERATION_TIME = "redis_client_operation_time_millis"

    /** Label for the client or pool instance name */
    const val NAME_LABEL = "name"

    /** Label for the Redis replication group identifier */
    const val REPLICATION_GROUP_ID_LABEL = "replication_group_id"

    /** Label for the Redis command type */
    const val COMMAND_LABEL = "command"

    /** Label for the Redis server address */
    const val REMOTE_ADDRESS_LABEL = "remote_address"

    /** Label for the client address */
    const val LOCAL_ADDRESS_LABEL = "local_address"
  }
}
