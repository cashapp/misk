package misk.web.jetty

import com.google.common.annotations.VisibleForTesting
import misk.metrics.Metrics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ConnectionMetrics @Inject internal constructor(metrics: Metrics) {
  val acceptedConnections = metrics.counter(
      "http_connections_total",
      "total number of connections accepted by jetty",
      labels
  )
  val connectionDurations = metrics.histogram(
      "http_connection_duration_ms",
      "average duration a incoming jetty connection is held open (in ms)",
      labels
  )
  val activeConnections = metrics.gauge(
      "http_connections_active",
      "number of currently active connections in jetty",
      labels
  )
  val bytesReceived = metrics.counter(
      "http_bytes_recvd_total",
      "total count of bytes received by jetty",
      labels
  )
  val bytesSent = metrics.counter(
      "http_bytes_sent_total",
      "total count of bytes sent through jetty",
      labels
  )
  val messagesReceived = metrics.counter(
      "http_msgs_recvd_total",
      "total count of HTTP messages received by jetty",
      labels
  )
  val messagesSent = metrics.counter(
      "http_msgs_sent_total",
      "total count of HTTP messages sent by jetty",
      labels
  )

  companion object {
    @VisibleForTesting internal val labels = listOf("protocol", "port")

    fun forPort(protocol: String, port: Int): Array<String> {
      return arrayOf(protocol, port.toString())
    }
  }
}
