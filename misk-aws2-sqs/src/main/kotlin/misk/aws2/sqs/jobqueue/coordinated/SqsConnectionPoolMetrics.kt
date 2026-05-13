package misk.aws2.sqs.jobqueue.coordinated

import io.prometheus.client.Gauge
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.metrics.v2.Metrics
import software.amazon.awssdk.http.HttpMetric
import software.amazon.awssdk.metrics.MetricCollection
import software.amazon.awssdk.metrics.MetricPublisher

/**
 * Prometheus gauges for the underlying AWS SDK v2 HTTP connection pool used by SQS clients.
 *
 * The values are reported by the AWS SDK v2 [HttpMetric] facility on every API call and reflect the most recent
 * observation. Each pool is labelled by [CLIENT_TYPE_LABEL]: "sending" or "receiving" (the receiving client also serves
 * `DeleteMessage` calls in this module).
 */
@Singleton
class SqsConnectionPoolMetrics @Inject internal constructor(metrics: Metrics) {
  internal val maxConnections: Gauge =
    metrics.gauge(
      name = MAX_CONNECTIONS,
      help = "Configured maximum number of HTTP connections for the SQS client connection pool.",
      labelNames = listOf(CLIENT_TYPE_LABEL),
    )

  internal val activeConnections: Gauge =
    metrics.gauge(
      name = ACTIVE_CONNECTIONS,
      help = "Current number of leased (in-use) HTTP connections for the SQS client connection pool.",
      labelNames = listOf(CLIENT_TYPE_LABEL),
    )

  internal val idleConnections: Gauge =
    metrics.gauge(
      name = IDLE_CONNECTIONS,
      help = "Current number of idle HTTP connections available in the SQS client connection pool.",
      labelNames = listOf(CLIENT_TYPE_LABEL),
    )

  internal val pendingConnections: Gauge =
    metrics.gauge(
      name = PENDING_CONNECTIONS,
      help = "Current number of requests waiting to acquire an HTTP connection from the SQS client connection pool.",
      labelNames = listOf(CLIENT_TYPE_LABEL),
    )

  internal fun publisherFor(clientType: ClientType): MetricPublisher = HttpPoolMetricPublisher(clientType.label)

  internal enum class ClientType(val label: String) {
    SENDING("sending"),
    RECEIVING("receiving"),
  }

  private inner class HttpPoolMetricPublisher(private val clientTypeLabel: String) : MetricPublisher {
    override fun publish(metricCollection: MetricCollection) {
      visit(metricCollection)
    }

    override fun close() {}

    private fun visit(collection: MetricCollection) {
      collection.metricValues(HttpMetric.MAX_CONCURRENCY).lastOrNull()?.let {
        maxConnections.labels(clientTypeLabel).set(it.toDouble())
      }
      collection.metricValues(HttpMetric.LEASED_CONCURRENCY).lastOrNull()?.let {
        activeConnections.labels(clientTypeLabel).set(it.toDouble())
      }
      collection.metricValues(HttpMetric.AVAILABLE_CONCURRENCY).lastOrNull()?.let {
        idleConnections.labels(clientTypeLabel).set(it.toDouble())
      }
      collection.metricValues(HttpMetric.PENDING_CONCURRENCY_ACQUIRES).lastOrNull()?.let {
        pendingConnections.labels(clientTypeLabel).set(it.toDouble())
      }
      collection.children().forEach { visit(it) }
    }
  }

  companion object {
    internal const val CLIENT_TYPE_LABEL = "client_type"
    internal const val MAX_CONNECTIONS = "sqs_client_max_connections"
    internal const val ACTIVE_CONNECTIONS = "sqs_client_active_connections"
    internal const val IDLE_CONNECTIONS = "sqs_client_idle_connections"
    internal const val PENDING_CONNECTIONS = "sqs_client_pending_connections"
  }
}
