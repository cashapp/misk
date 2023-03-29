package misk.redis

import misk.metrics.v2.Metrics

class RedisClientMetrics(metrics: Metrics) {
  internal val maxTotalConnectionsGauge = metrics.gauge(
    name = MAX_TOTAL_CONNECTIONS,
    help = """
           Max number of connections for the misk-redis client connection pool.
           This is configured on app startup.
           """.trimIndent(),
  )
  internal val maxIdleConnectionsGauge = metrics.gauge(
    name = MAX_IDLE_CONNECTIONS,
    help = """
           Max number of idle connections for the misk-redis client connection pool.
           This is configured on app startup.
           """.trimIndent(),
  )
  internal val activeConnectionsGauge = metrics.gauge(
    name = ACTIVE_CONNECTIONS,
    help = "Current number of active connections for the misk-redis client connection pool.",
  )
  internal val idleConnectionsGauge = metrics.gauge(
    name = IDLE_CONNECTIONS,
    help = "Current number of idle connections for the misk-redis client connection pool.",
  )
  internal val destroyedConnectionsCounter = metrics.counter(
    name = DESTROYED_CONNECTIONS_TOTAL,
    help = """
           The total count of connections dropped from the pool.
           Connections are dropped when they fail validation, and may be in an inconsistent state.
           """.trimIndent(),
  )
  private val operationTime = metrics.histogram(
    name = OPERATION_TIME,
    help = "The time it took in seconds, as reported by the client, to complete an operation.",
    labelNames = listOf("command"),
  )

  internal fun <T> timed(commandName: String, block: () -> T): T {
    return operationTime.labels(commandName).time(block)
  }

  companion object {
    internal const val MAX_TOTAL_CONNECTIONS = "redis_client_max_total_connections"
    internal const val MAX_IDLE_CONNECTIONS = "redis_client_max_idle_connections"
    internal const val IDLE_CONNECTIONS = "redis_client_idle_connections"
    internal const val ACTIVE_CONNECTIONS = "redis_client_active_connections"
    internal const val DESTROYED_CONNECTIONS_TOTAL = "redis_client_pool_destroyed_connections_total"
    internal const val OPERATION_TIME = "redis_client_operation_time_seconds"
  }
}
