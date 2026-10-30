package misk.opentelemetry.jetty

import com.google.common.util.concurrent.AbstractIdleService
import io.opentelemetry.api.OpenTelemetry
import java.util.WeakHashMap
import java.util.function.LongSupplier
import javax.management.MBeanServerFactory
import misk.web.jetty.MeasuredThreadPool
import org.eclipse.jetty.jmx.MBeanContainer
import org.eclipse.jetty.server.handler.StatisticsHandler
import org.eclipse.jetty.util.thread.QueuedThreadPool

/**
 * Registers Jetty's MBeans for upstream JMX metrics and observes only custom servlet statistics directly. Misk's legacy
 * Prometheus collectors continue to read the same handler and measured pool independently. Servlet statistics
 * instruments belong to the SDK rather than to this service (see [ServletStatistics]), so they stay registered after it
 * stops.
 */
class OtelJettyMetricsService(
  private val openTelemetry: OpenTelemetry,
  private val statistics: StatisticsHandler,
  private val threadPool: MeasuredThreadPool,
) : AbstractIdleService() {
  private val metrics = JettyMetrics(openTelemetry)
  private val registrations = ArrayList<AutoCloseable>(4)

  override fun startUp() {
    // No-op meters (no SDK bound, or a test binding): skip the MBeans and JMX polling they would discard.
    if (openTelemetry === OpenTelemetry.noop()) return
    try {
      val server = checkNotNull(statistics.server) { "Jetty StatisticsHandler is not attached to a server" }
      // Jetty's inherited listener replays beans already attached to the running server and its managed children.
      // Keep this MBean server private so another Jetty instance in the process cannot contribute to our metrics.
      val container = MBeanContainer(MBeanServerFactory.newMBeanServer(), false)
      registrations.add(
        AutoCloseable {
          try {
            server.removeBean(container)
          } finally {
            container.destroy()
          }
        }
      )
      check(server.addBean(container)) { "Jetty MBeanContainer could not be attached to the server" }
      // Misk starts filesystem Unix socket connectors before adding them, so the server holds them as unmanaged beans
      // and the inherited listener does not descend into their selectors. Attach the container to those directly.
      server.connectors
        .filter { server.isUnmanaged(it) }
        .forEach { connector ->
          check(connector.addBean(container, false)) { "Jetty MBeanContainer could not be attached to $connector" }
          registrations.add(AutoCloseable { connector.removeBean(container) })
        }
      registrations.add(JettyJmxMetrics.start(openTelemetry, container.mBeanServer))
      ServletStatistics.observe(openTelemetry, statistics)
      // Misk's non-queued ExecutorThreadPool has no upstream QueuedThreadPool MBean.
      // Only this alternate pool needs the existing native thread observations.
      if (server.threadPool !is QueuedThreadPool) registrations.add(metrics.observeThreadPool(MeasuredThreads()))
    } catch (failure: Throwable) {
      try {
        shutDown()
      } catch (closeFailure: Throwable) {
        failure.addSuppressed(closeFailure)
      }
      throw failure
    }
  }

  override fun shutDown() {
    var failure: Throwable? = null
    while (registrations.isNotEmpty()) {
      try {
        registrations.removeAt(registrations.lastIndex).close()
      } catch (closeFailure: Throwable) {
        if (failure == null) failure = closeFailure else failure.addSuppressed(closeFailure)
      }
    }
    failure?.let { throw it }
  }

  /**
   * Jetty 11 returns cumulative counters as `int`, so they wrap after 2^31 events; each counter is widened with
   * [WrappingIntCounter], and time and byte totals use [ResetCarryingCounter], so exported sums stay monotonic across
   * overflow and statistics resets.
   *
   * The OpenTelemetry SDK lives for the whole process and keeps each instrument's last observed total, while a hot
   * reload (a new injector in the same JVM) builds a new Jetty server whose statistics start at zero. One adapter per
   * SDK therefore outlives the service and reads whichever handler the latest service attached, so totals carry across
   * reloads instead of exporting negative deltas. Its instruments are registered once and never closed: with delta
   * temporality the SDK forgets an asynchronous counter's last value at any collection where no callback reports it, so
   * a collection between the old service stopping and the new one starting would otherwise export the whole carried
   * total again. Until then the instruments keep reading the stopped handler, which retains its final totals.
   */
  private class ServletStatistics : JettyServletStatistics {
    @Volatile private lateinit var statistics: StatisticsHandler
    private val statisticsDurationMs = LongSupplier { statistics.statsOnMs }
    // Jetty 11 measures statsOnMs on the wall clock.
    private val clockMs = LongSupplier(System::currentTimeMillis)
    private val requests = WrappingIntCounter(statisticsDurationMs, clockMs)
    private val dispatched = WrappingIntCounter(statisticsDurationMs, clockMs)
    private val asyncRequests = WrappingIntCounter(statisticsDurationMs, clockMs)
    private val asyncDispatches = WrappingIntCounter(statisticsDurationMs, clockMs)
    private val expires = WrappingIntCounter(statisticsDurationMs, clockMs)
    // Jetty 11's statsReset() leaves errors and responsesThrown untouched, so only wraps apply to them.
    private val errors = WrappingIntCounter.neverReset()
    private val responsesThrown = WrappingIntCounter.neverReset()
    private val responsesByClass = Array(5) { WrappingIntCounter(statisticsDurationMs, clockMs) }
    private val requestTime = ResetCarryingCounter(statisticsDurationMs, clockMs)
    private val dispatchTime = ResetCarryingCounter(statisticsDurationMs, clockMs)
    private val responsesBytes = ResetCarryingCounter(statisticsDurationMs, clockMs)
    private val intCounters =
      listOf(requests, dispatched, asyncRequests, asyncDispatches, expires, errors, responsesThrown) + responsesByClass
    private val totals = listOf(requestTime, dispatchTime, responsesBytes)

    /**
     * Reads [handler] from now on. The previous handler is read one last time, so work since the last collection is
     * kept, and its totals carry forward, as the new handler's statistics start at zero. Total reads synchronize on
     * this adapter too, so an SDK collection cannot read the old handler after its totals were carried.
     */
    @Synchronized
    fun attach(handler: StatisticsHandler) {
      if (this::statistics.isInitialized && statistics !== handler) {
        readTotals()
        intCounters.forEach(WrappingIntCounter::carry)
        totals.forEach(ResetCarryingCounter::carry)
      }
      statistics = handler
    }

    private fun readTotals() {
      requests()
      requestTimeTotalSeconds()
      dispatches()
      dispatchTimeTotalSeconds()
      asyncRequests()
      asyncDispatches()
      asyncExpires()
      errors()
      for (statusClass in 1..5) responses(statusClass)
      responsesThrown()
      responsesBytes()
    }

    @Synchronized override fun requests(): Long = requests.extend { statistics.requests }

    override fun requestsActive(): Long = statistics.requestsActive.toLong()

    override fun requestsActiveMax(): Long = statistics.requestsActiveMax.toLong()

    override fun requestTimeMaxSeconds(): Double = statistics.requestTimeMax / 1_000.0

    @Synchronized
    override fun requestTimeTotalSeconds(): Double = requestTime.extend { statistics.requestTimeTotal / 1_000.0 }

    @Synchronized override fun dispatches(): Long = dispatched.extend { statistics.dispatched }

    override fun dispatchesActive(): Long = statistics.dispatchedActive.toLong()

    override fun dispatchesActiveMax(): Long = statistics.dispatchedActiveMax.toLong()

    override fun dispatchTimeMaxSeconds(): Double = statistics.dispatchedTimeMax / 1_000.0

    @Synchronized
    override fun dispatchTimeTotalSeconds(): Double = dispatchTime.extend { statistics.dispatchedTimeTotal / 1_000.0 }

    @Synchronized override fun asyncRequests(): Long = asyncRequests.extend { statistics.asyncRequests }

    override fun asyncRequestsWaiting(): Long = statistics.asyncRequestsWaiting.toLong()

    override fun asyncRequestsWaitingMax(): Long = statistics.asyncRequestsWaitingMax.toLong()

    @Synchronized override fun asyncDispatches(): Long = asyncDispatches.extend { statistics.asyncDispatches }

    @Synchronized override fun asyncExpires(): Long = expires.extend { statistics.expires }

    @Synchronized override fun errors(): Long = errors.extend { statistics.errors }

    @Synchronized
    override fun responses(statusClass: Int): Long {
      require(statusClass in 1..5) { "Invalid HTTP status class: $statusClass" }
      return responsesByClass[statusClass - 1].extend {
        when (statusClass) {
          1 -> statistics.responses1xx
          2 -> statistics.responses2xx
          3 -> statistics.responses3xx
          4 -> statistics.responses4xx
          else -> statistics.responses5xx
        }
      }
    }

    @Synchronized override fun responsesThrown(): Long = responsesThrown.extend { statistics.responsesThrown }

    @Synchronized
    override fun responsesBytes(): Long = responsesBytes.extend { statistics.responsesBytesTotal.toDouble() }.toLong()

    override fun uptimeSeconds(): Double = statistics.statsOnMs / 1_000.0

    companion object {
      private val byOpenTelemetry = WeakHashMap<OpenTelemetry, ServletStatistics>()

      /** Reads [statistics] from now on, registering the instruments on the first call for [openTelemetry]. */
      fun observe(openTelemetry: OpenTelemetry, statistics: StatisticsHandler) {
        synchronized(byOpenTelemetry) {
          val existing = byOpenTelemetry[openTelemetry]
          if (existing != null) {
            existing.attach(statistics)
          } else {
            val created = ServletStatistics()
            created.attach(statistics)
            // Deliberately never closed; the callbacks hold the adapter, which does not hold the SDK.
            JettyMetrics(openTelemetry).observeStatistics(created)
            byOpenTelemetry[openTelemetry] = created
          }
        }
      }
    }
  }

  private inner class MeasuredThreads : JettyThreads {
    override fun poolSize(): Long = threadPool.poolSize().toLong()

    override fun active(): Long = threadPool.activeCount().toLong()

    override fun idle(): Long = (threadPool.poolSize() - threadPool.activeCount()).coerceAtLeast(0).toLong()

    override fun max(): Long = threadPool.maxPoolSize().toLong()

    override fun queued(): Long = threadPool.queueSize().toLong()
  }
}
