package misk.redis

import com.google.common.base.Stopwatch
import com.google.common.base.Ticker
import misk.metrics.v2.Metrics
import java.time.Duration
import java.util.concurrent.TimeUnit

class RedisClientMetrics(private val ticker: Ticker, metrics: Metrics) {
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
    help = "The time it took in milliseconds, as reported by the client, to complete an operation.",
    labelNames = listOf("command"),
  )

  fun <T> timed(commandName: String, block: () -> T): T {
    val stopwatch = Stopwatch.createStarted(ticker)
    val result = runCatching { block() }
    stopwatch.stop()
    operationTime.labels(commandName).observe(stopwatch.elapsed().toMillis().toDouble())
    return result.getOrThrow()
  }

  companion object {
    internal const val MAX_TOTAL_CONNECTIONS = "redis_client_max_total_connections"
    internal const val MAX_IDLE_CONNECTIONS = "redis_client_max_idle_connections"
    internal const val IDLE_CONNECTIONS = "redis_client_idle_connections"
    internal const val ACTIVE_CONNECTIONS = "redis_client_active_connections"
    internal const val DESTROYED_CONNECTIONS_TOTAL = "redis_client_pool_destroyed_connections_total"
    internal const val OPERATION_TIME = "redis_client_operation_time_millis"
  }
}
