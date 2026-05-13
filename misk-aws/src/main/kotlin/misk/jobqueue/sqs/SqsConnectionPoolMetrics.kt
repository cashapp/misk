package misk.jobqueue.sqs

import com.amazonaws.Request
import com.amazonaws.Response
import com.amazonaws.metrics.RequestMetricCollector
import com.amazonaws.util.AWSRequestMetrics
import com.amazonaws.util.TimingInfo
import io.prometheus.client.Gauge
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.metrics.v2.Metrics

/**
 * Prometheus gauges for the underlying AWS SDK v1 HTTP connection pool used by SQS clients.
 *
 * The values are reported per-request by the AWS SDK v1 metrics facility (`HttpClientPool*` fields on
 * [AWSRequestMetrics.Field]) and reflect the most recent observation. Each pool is labelled by [CLIENT_TYPE_LABEL]:
 * "sending" or "receiving" (the receiving client also serves `DeleteMessage` calls in this module).
 */
@Singleton
class SqsConnectionPoolMetrics @Inject internal constructor(metrics: Metrics) {
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

  internal fun collectorFor(clientType: ClientType): RequestMetricCollector = HttpPoolMetricCollector(clientType.label)

  internal enum class ClientType(val label: String) {
    SENDING("sending"),
    RECEIVING("receiving"),
  }

  private inner class HttpPoolMetricCollector(private val clientTypeLabel: String) : RequestMetricCollector() {
    override fun collectMetrics(request: Request<*>?, response: Response<*>?) {
      val metrics = request?.awsRequestMetrics?.timingInfo ?: return
      lastValueOf(metrics, AWSRequestMetrics.Field.HttpClientPoolLeasedCount)?.let {
        activeConnections.labels(clientTypeLabel).set(it)
      }
      lastValueOf(metrics, AWSRequestMetrics.Field.HttpClientPoolAvailableCount)?.let {
        idleConnections.labels(clientTypeLabel).set(it)
      }
      lastValueOf(metrics, AWSRequestMetrics.Field.HttpClientPoolPendingCount)?.let {
        pendingConnections.labels(clientTypeLabel).set(it)
      }
    }

    private fun lastValueOf(timingInfo: TimingInfo, field: AWSRequestMetrics.Field): Double? {
      val counter: Number = timingInfo.getCounter(field.name) ?: return null
      return counter.toDouble()
    }
  }

  companion object {
    internal const val CLIENT_TYPE_LABEL = "client_type"
    internal const val ACTIVE_CONNECTIONS = "sqs_client_active_connections"
    internal const val IDLE_CONNECTIONS = "sqs_client_idle_connections"
    internal const val PENDING_CONNECTIONS = "sqs_client_pending_connections"
  }
}
