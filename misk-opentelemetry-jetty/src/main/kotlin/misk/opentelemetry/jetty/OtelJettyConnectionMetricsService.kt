package misk.opentelemetry.jetty

import com.google.common.util.concurrent.AbstractScheduledService
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.DoubleHistogram
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongUpDownCounter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import org.eclipse.jetty.io.Connection
import org.eclipse.jetty.io.EndPoint
import org.eclipse.jetty.server.Connector
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.StatisticsHandler
import org.eclipse.jetty.unixdomain.server.UnixDomainServerConnector
import org.eclipse.jetty.unixsocket.server.UnixSocketConnector

private const val REFRESH_INTERVAL_MS = 500L

/**
 * Registers OpenTelemetry connection listeners on Jetty connectors and periodically polls active connections.
 *
 * Tracks connection-level metrics via OTel:
 * - jetty.connection.duration (histogram) - Connection lifetime in seconds, recorded on close or observer shutdown
 * - jetty.connection.active (UpDownCounter) - Currently open connections
 * - jetty.connections.accepted (counter) - Connections accepted
 * - jetty.connection.bytes.received / jetty.connection.bytes.sent (counters) - Transport bytes including headers
 * - jetty.connection.messages.received / jetty.connection.messages.sent (counters) - Connection messages
 *
 * The scheduled polling keeps bytes and messages fresh for long-lived connections (HTTP/2, keep-alive). This
 * intentionally does not attempt to re-export Jetty StatisticsHandler's request counters: those live in
 * [OtelJettyMetricsService]. Connection bytes include headers, unlike request body size in OTel conventions.
 *
 * OpenTelemetry semantic conventions define no HTTP server connection metrics, so these use the `jetty.` namespace
 * rather than `http.server.`. Unix domain socket connections carry the socket path as `network.local.address`, so
 * services listening on several sockets can tell them apart. [observedUnixDomainSockets] limits which sockets are
 * observed; `null` observes all of them.
 */
class OtelJettyConnectionMetricsService
@JvmOverloads
constructor(
  private val openTelemetry: OpenTelemetry,
  private val statisticsHandler: StatisticsHandler,
  appName: String,
  private val observedUnixDomainSockets: Set<String>? = null,
) : AbstractScheduledService() {

  private val meter = openTelemetry.getMeter(appName)

  private val connectionDurationHistogram: DoubleHistogram =
    meter
      .histogramBuilder("jetty.connection.duration")
      .setDescription("Duration of HTTP server connections")
      .setUnit("s")
      .build()

  private val activeConnectionsCounter: LongUpDownCounter =
    meter
      .upDownCounterBuilder("jetty.connection.active")
      .setDescription("Number of active HTTP server connections")
      .setUnit("{connection}")
      .build()

  // Includes HTTP headers, not just body.
  private val bytesReceivedCounter: LongCounter =
    meter
      .counterBuilder("jetty.connection.bytes.received")
      .setDescription("Bytes received on HTTP server connections (includes headers)")
      .setUnit("By")
      .build()

  // Includes HTTP headers, not just body.
  private val bytesSentCounter: LongCounter =
    meter
      .counterBuilder("jetty.connection.bytes.sent")
      .setDescription("Bytes sent on HTTP server connections (includes headers)")
      .setUnit("By")
      .build()

  private val acceptedConnectionsCounter: LongCounter =
    meter.counterBuilder("jetty.connections.accepted").setUnit("{connection}").build()

  private val messagesReceivedCounter: LongCounter =
    meter.counterBuilder("jetty.connection.messages.received").setUnit("{message}").build()

  private val messagesSentCounter: LongCounter =
    meter.counterBuilder("jetty.connection.messages.sent").setUnit("{message}").build()

  private val listeners = mutableListOf<Pair<Connector, OtelJettyConnectionListener>>()

  override fun startUp() {
    // No-op meters (no SDK bound, or a test binding): attach no listeners, so each refresh has nothing to do.
    if (openTelemetry === OpenTelemetry.noop()) return
    val server = checkNotNull(statisticsHandler.server) { "Jetty StatisticsHandler is not attached to a server" }

    server.connectors.forEach { connector ->
      // Misk's dedicated liveness connector is intentionally excluded from its legacy connection collector.
      if (connector.name == "health") return@forEach
      val attributesBuilder = Attributes.builder().put(AttributeKey.stringKey("network.protocol.name"), "http")

      if (connector is ServerConnector) {
        attributesBuilder
          .put(AttributeKey.stringKey("network.transport"), "tcp")
          .put(AttributeKey.longKey("server.port"), connector.port.toLong())
      } else {
        val socketPath = connector.unixDomainSocketPath() ?: return@forEach
        if (observedUnixDomainSockets != null && socketPath !in observedUnixDomainSockets) return@forEach
        attributesBuilder
          .put(AttributeKey.stringKey("network.transport"), "unix")
          .put(AttributeKey.stringKey("network.local.address"), socketPath)
      }

      val listener =
        OtelJettyConnectionListener(
          durationHistogram = connectionDurationHistogram,
          bytesReceivedCounter = bytesReceivedCounter,
          bytesSentCounter = bytesSentCounter,
          acceptedCounter = acceptedConnectionsCounter,
          messagesReceivedCounter = messagesReceivedCounter,
          messagesSentCounter = messagesSentCounter,
          activeCounter = activeConnectionsCounter,
          attributes = attributesBuilder.build(),
        )
      connector.addBean(listener)
      listeners.add(connector to listener)
    }
  }

  override fun runOneIteration() {
    listeners.forEach { (_, listener) -> listener.refreshMetrics() }
  }

  override fun shutDown() {
    listeners.forEach { (connector, listener) ->
      connector.removeBean(listener)
      listener.stopObserving()
    }
    listeners.clear()
  }

  override fun scheduler(): Scheduler =
    Scheduler.newFixedRateSchedule(REFRESH_INTERVAL_MS, REFRESH_INTERVAL_MS, TimeUnit.MILLISECONDS)
}

/** Misk binds filesystem sockets with [UnixDomainServerConnector] and abstract sockets with [UnixSocketConnector]. */
private fun Connector.unixDomainSocketPath(): String? =
  when (this) {
    is UnixDomainServerConnector -> unixDomainPath?.toString()
    is UnixSocketConnector -> unixSocket
    else -> null
  }

/**
 * Records connection metrics once per socket. Jetty opens several [Connection]s on one socket: TLS adds an
 * SslConnection beneath the decrypted HTTP connection, ALPN negotiates before handing off to HTTP/1.1 or HTTP/2, and
 * cleartext HTTP/2 upgrades from HTTP/1.1. All of them reach this listener, so state is keyed by the socket's network
 * [EndPoint]: accepted, active, and duration count the socket, and bytes come only from connections directly on it, so
 * they are transport bytes (encrypted under TLS). Messages come from whichever layer counts them; in Jetty 11 only
 * HTTP/1.1 does. HTTP/2 connections count neither bytes nor messages, so a cleartext HTTP/2 socket reports bytes only
 * until it upgrades.
 */
class OtelJettyConnectionListener(
  private val durationHistogram: DoubleHistogram,
  private val activeCounter: LongUpDownCounter,
  private val bytesReceivedCounter: LongCounter,
  private val bytesSentCounter: LongCounter,
  private val acceptedCounter: LongCounter,
  private val messagesReceivedCounter: LongCounter,
  private val messagesSentCounter: LongCounter,
  private val attributes: Attributes,
) : Connection.Listener {
  private val sockets = ConcurrentHashMap<EndPoint, SocketState>()
  @Volatile private var stopped = false

  @Synchronized
  override fun onOpened(connection: Connection) {
    if (stopped) return
    val socket = connection.endPoint.socket()
    val state =
      sockets.getOrPut(socket) {
        acceptedCounter.add(1, attributes)
        activeCounter.add(1, attributes)
        SocketState(System.nanoTime())
      }
    state.add(connection, countsBytes = connection.endPoint === socket)
  }

  @Synchronized
  override fun onClosed(connection: Connection) {
    if (stopped) return
    val socket = connection.endPoint.socket()
    val state = sockets[socket] ?: return
    // Record final values even if the connection opened and closed between scheduled polls.
    state.remove(connection)
    // A connection closing on a socket that stays open is an upgrade; its successor opens next.
    if (state.isEmpty() && !socket.isOpen) finish(socket, state)
  }

  /** Refreshes connections that remain open; each socket serializes its poll with a concurrent close. */
  fun refreshMetrics() {
    if (stopped) return
    sockets.values.forEach(SocketState::refresh)
    finishClosedSockets()
  }

  @Synchronized
  private fun finishClosedSockets() {
    if (stopped) return
    sockets.entries
      .filter { (socket, state) -> state.isEmpty() && !socket.isOpen }
      .forEach { (socket, state) -> finish(socket, state) }
  }

  private fun finish(socket: EndPoint, state: SocketState) {
    sockets.remove(socket)
    durationHistogram.record(state.elapsedSeconds(), attributes)
    activeCounter.add(-1, attributes)
  }

  @Synchronized
  fun stopObserving() {
    // Jetty remains running until after this service stops. Preserve final traffic and the lifetime of
    // sockets that are still open when their listener is removed.
    sockets.values.forEach { state ->
      state.close()
      durationHistogram.record(state.elapsedSeconds(), attributes)
    }
    stopped = true
    activeCounter.add(-sockets.size.toLong(), attributes)
    sockets.clear()
  }

  private inner class SocketState(private val startNanos: Long) {
    private val connections = HashMap<Connection, ConnectionCounts>()

    fun elapsedSeconds(): Double = (System.nanoTime() - startNanos) / 1_000_000_000.0

    @Synchronized
    fun add(connection: Connection, countsBytes: Boolean) {
      connections.putIfAbsent(connection, ConnectionCounts(countsBytes))
    }

    @Synchronized
    fun remove(connection: Connection) {
      connections.remove(connection)?.record(connection)
    }

    @Synchronized fun isEmpty(): Boolean = connections.isEmpty()

    @Synchronized
    fun refresh() {
      if (stopped) return
      connections.forEach { (connection, counts) -> counts.record(connection) }
    }

    @Synchronized
    fun close() {
      connections.forEach { (connection, counts) -> counts.record(connection) }
      connections.clear()
    }
  }

  /** Jetty returns -1 from counters a connection layer does not track; those report nothing. */
  private inner class ConnectionCounts(private val countsBytes: Boolean) {
    private var bytesIn = 0L
    private var bytesOut = 0L
    private var messagesIn = 0L
    private var messagesOut = 0L

    fun record(connection: Connection) {
      if (countsBytes) {
        bytesIn = recordDelta(bytesReceivedCounter, connection.bytesIn, bytesIn)
        bytesOut = recordDelta(bytesSentCounter, connection.bytesOut, bytesOut)
      }
      messagesIn = recordDelta(messagesReceivedCounter, connection.messagesIn, messagesIn)
      messagesOut = recordDelta(messagesSentCounter, connection.messagesOut, messagesOut)
    }

    private fun recordDelta(counter: LongCounter, current: Long, last: Long): Long {
      if (current <= last) return last
      counter.add(current - last, attributes)
      return current
    }
  }
}

/** The network endpoint beneath any wrapping layers, such as TLS's decrypted endpoint. */
private fun EndPoint.socket(): EndPoint {
  var endPoint = this
  while (endPoint is EndPoint.Wrapper) endPoint = endPoint.unwrap()
  return endPoint
}
