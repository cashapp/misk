package misk.web.jetty

import org.eclipse.jetty.io.Connection
import org.eclipse.jetty.util.component.AbstractLifeCycle
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal class ConnectionListener(
  protocol: String,
  port: Int,
  private val metrics: ConnectionMetrics
) : AbstractLifeCycle(), Connection.Listener {

  private val totalBytesReceived = AtomicLong()
  private val totalBytesSent = AtomicLong()
  private val totalMessagesReceived = AtomicLong()
  private val totalMessagesSent = AtomicLong()

  private val connections = Collections.newSetFromMap(ConcurrentHashMap<ConnectionKey, Boolean>())
  private val labels = ConnectionMetrics.forPort(protocol, port)

  override fun onOpened(connection: Connection) {
    connections.add(ConnectionKey(connection))
    metrics.activeConnections.labels(*labels).inc()
    metrics.acceptedConnections.labels(*labels).inc()
  }

  override fun onClosed(connection: Connection) {
    // Force a refresh so that we gather the final stats on the connection
    refreshMetrics()

    if (connections.remove(ConnectionKey(connection))) {
      metrics.activeConnections.labels(*labels).dec()
    }
  }

  fun refreshMetrics() {
    var bytesReceivedSnapshot = 0L
    var bytesSentSnapshot = 0L
    var messagesReceivedSnapshot = 0L
    var messagesSentSnapshot = 0L

    connections.forEach {
      val bytesIn = it.connection.bytesIn
      if (bytesIn > 0) bytesReceivedSnapshot += bytesIn

      val bytesOut = it.connection.bytesOut
      if (bytesOut > 0) bytesSentSnapshot += bytesOut

      val messagesIn = it.connection.messagesIn
      if (messagesIn > 0) messagesReceivedSnapshot += messagesIn

      val messagesOut = it.connection.messagesOut
      if (messagesOut > 0) messagesSentSnapshot += messagesOut

      val connectionDuration = System.currentTimeMillis() - it.connection.createdTimeStamp
      metrics.connectionDurations.record(connectionDuration.toDouble(), *labels)
    }

    // Compute any diffs with the last time we took a snapshot, and apply those diffs
    // to the counter
    val bytesReceivedDiff =
        bytesReceivedSnapshot - totalBytesReceived.getAndSet(bytesReceivedSnapshot)
    val bytesSentDiff =
        bytesSentSnapshot - totalBytesSent.getAndSet(bytesSentSnapshot)
    val messagesReceivedDiff =
        messagesReceivedSnapshot - totalMessagesReceived.getAndSet(messagesReceivedSnapshot)
    val messagesSentDiff =
        messagesSentSnapshot - totalMessagesSent.getAndSet(messagesSentSnapshot)

    if (bytesSentDiff > 0) {
      metrics.bytesSent.labels(*labels).inc(bytesSentDiff.toDouble())
    }

    if (bytesReceivedDiff > 0) {
      metrics.bytesReceived.labels(*labels).inc(bytesReceivedDiff.toDouble())
    }

    if (messagesSentDiff > 0) {
      metrics.messagesSent.labels(*labels).inc(messagesSentDiff.toDouble())
    }

    if (messagesReceivedDiff > 0) {
      metrics.messagesReceived.labels(*labels).inc(messagesReceivedDiff.toDouble())
    }
  }

  private class ConnectionKey(val connection: Connection) {
    override fun equals(other: Any?): Boolean {
      return (other as? ConnectionKey)?.connection === connection
    }

    override fun hashCode() = System.identityHashCode(connection)
  }
}