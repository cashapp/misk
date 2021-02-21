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

  // not thread safe since it's only used by refreshMetrics
  private var previousSnapshot = Snapshot.empty()

  // see the Accumulator doc for thread safety
  private val removedTotals = Accumulator()
  private val activeConnections =
    Collections.newSetFromMap(ConcurrentHashMap<ConnectionKey, Boolean>())
  private val labels = ConnectionMetrics.forPort(protocol, port)

  override fun onOpened(connection: Connection) {
    activeConnections.add(ConnectionKey(connection))
    metrics.activeConnections.labels(*labels).inc()
    metrics.acceptedConnections.labels(*labels).inc()
  }

  override fun onClosed(connection: Connection) {
    if (activeConnections.remove(ConnectionKey(connection))) {
      // save the stats for the removed connection so they are included in the next call to
      // refreshMetrics.
      removedTotals.addConnection(connection)
      recordDuration(connection)
      metrics.activeConnections.labels(*labels).dec()
    }
  }

  fun refreshMetrics() {
    val newTotals = Accumulator()
    activeConnections.forEach {
      newTotals.addConnection(it.connection)
      recordDuration(it.connection)
    }
    // add any connections that were removed since the previous refresh
    newTotals.addSnapshot(removedTotals.snapshotAndReset())

    // Compute any diffs with the last time we took a snapshot, and apply those diffs
    // to the counter
    val newSnapshot = newTotals.snapshotAndReset()
    val diff = newSnapshot - previousSnapshot
    previousSnapshot = newSnapshot

    if (diff.bytesSent > 0) {
      metrics.bytesSent.labels(*labels).inc(diff.bytesSent.toDouble())
    }

    if (diff.bytesReceived > 0) {
      metrics.bytesReceived.labels(*labels).inc(diff.bytesReceived.toDouble())
    }

    if (diff.messagesSent > 0) {
      metrics.messagesSent.labels(*labels).inc(diff.messagesSent.toDouble())
    }

    if (diff.messagesReceived > 0) {
      metrics.messagesReceived.labels(*labels).inc(diff.messagesReceived.toDouble())
    }
  }

  private fun recordDuration(connection: Connection) {
    val connectionDuration = System.currentTimeMillis() - connection.createdTimeStamp
    metrics.connectionDurations.record(connectionDuration.toDouble(), *labels)
  }

  private class ConnectionKey(val connection: Connection) {
    override fun equals(other: Any?): Boolean {
      return (other as? ConnectionKey)?.connection === connection
    }

    override fun hashCode() = System.identityHashCode(connection)
  }

  /**
   * An immutable snapshot from an [Accumulator]
   */
  private data class Snapshot(
    val bytesReceived: Long,
    val bytesSent: Long,
    val messagesReceived: Long,
    val messagesSent: Long
  ) {

    operator fun minus(other: Snapshot): Snapshot {
      return Snapshot(
        bytesReceived - other.bytesReceived,
        bytesSent - other.bytesSent,
        messagesReceived - other.messagesReceived,
        messagesSent - other.messagesSent
      )
    }

    companion object {
      fun empty() = Snapshot(0, 0, 0, 0)
    }
  }

  /**
   * A mutable accumulator of metric data.
   *
   * This Accumulator is not entirely thread safe as an intentional trade off to avoid locking. The
   * individual accumulated metrics are thread safe (use AtomicLongs), but the group of metrics is
   * not updated atomically. When a snapshot is generated it might include a partial set of metrics
   * from an in-flight addition. Since metrics are inherently lossy and compressed this is a reasonable
   * trade off to avoid locking every jetty thread.
   */
  private data class Accumulator(
    private val bytesReceived: AtomicLong = AtomicLong(),
    private val bytesSent: AtomicLong = AtomicLong(),
    private val messagesReceived: AtomicLong = AtomicLong(),
    private val messagesSent: AtomicLong = AtomicLong()
  ) {

    /**
     * Update the accumulator with metrics from the [Connection].
     */
    fun addConnection(connection: Connection) {
      bytesReceived.addAndGet(connection.bytesIn)
      bytesSent.addAndGet(connection.bytesOut)
      messagesReceived.addAndGet(connection.messagesIn)
      messagesSent.addAndGet(connection.messagesOut)
    }

    /**
     * Update the accumulator with metrics from a [Snapshot].
     */
    fun addSnapshot(snapshot: Snapshot) {
      bytesReceived.addAndGet(snapshot.bytesReceived)
      bytesSent.addAndGet(snapshot.bytesSent)
      messagesReceived.addAndGet(snapshot.messagesReceived)
      messagesSent.addAndGet(snapshot.messagesSent)
    }

    fun snapshotAndReset(): Snapshot {
      return Snapshot(
        bytesReceived.getAndSet(0),
        bytesSent.getAndSet(0),
        messagesReceived.getAndSet(0),
        messagesSent.getAndSet(0)
      )
    }
  }
}
