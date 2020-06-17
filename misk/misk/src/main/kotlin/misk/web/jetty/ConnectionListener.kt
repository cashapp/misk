package misk.web.jetty

import org.eclipse.jetty.io.Connection
import org.eclipse.jetty.util.component.AbstractLifeCycle
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
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
  private val openedConnections = ConcurrentLinkedQueue<ConnectionKey>()
  private val closedConnections = ConcurrentLinkedQueue<ConnectionKey>()
  private val labels = ConnectionMetrics.forPort(protocol, port)

  // Updating prometheus metrics requires monitor locks and its best to avoid acquiring those
  // with the high number of jetty threads. Instead we queue up changes to the connection and
  // process the changes when metrics are refreshed by a single background thread.
  override fun onOpened(connection: Connection) {
    openedConnections.add(ConnectionKey(connection))
  }

  override fun onClosed(connection: Connection) {
    closedConnections.add(ConnectionKey(connection))
  }

  fun refreshMetrics() {
    while (openedConnections.isNotEmpty()) {
      connections.add(openedConnections.remove())
      metrics.activeConnections.labels(*labels).inc()
      metrics.acceptedConnections.labels(*labels).inc()
    }

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

    while (closedConnections.isNotEmpty()) {
      connections.remove(closedConnections.remove())
      metrics.activeConnections.labels(*labels).dec()
    }
  }

  private class ConnectionKey(val connection: Connection) {
    override fun equals(other: Any?): Boolean {
      return (other as? ConnectionKey)?.connection === connection
    }

    override fun hashCode() = System.identityHashCode(connection)
  }
}
