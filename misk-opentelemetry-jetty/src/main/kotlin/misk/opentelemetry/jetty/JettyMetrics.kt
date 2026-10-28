package misk.opentelemetry.jetty

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter

/** Jetty statistics expressed independently of the Jetty version that supplies them. */
interface JettyStatistics {
  fun requests(): Long

  fun requestsActive(): Long

  fun requestsActiveMax(): Long

  fun requestTimeMaxSeconds(): Double

  fun requestTimeTotalSeconds(): Double

  fun dispatches(): Long

  fun dispatchesActive(): Long

  fun dispatchesActiveMax(): Long

  fun dispatchTimeMaxSeconds(): Double

  fun dispatchTimeTotalSeconds(): Double

  /** HTTP status class, from 1 through 5. */
  fun responses(statusClass: Int): Long

  fun responsesBytes(): Long

  fun uptimeSeconds(): Double
}

/** Additional statistics exposed by Jetty 11's servlet StatisticsHandler. */
interface JettyServletStatistics : JettyStatistics {
  fun asyncRequests(): Long

  fun asyncRequestsWaiting(): Long

  fun asyncRequestsWaitingMax(): Long

  fun asyncDispatches(): Long

  fun asyncExpires(): Long

  fun errors(): Long

  fun responsesThrown(): Long
}

/** Native fallback when Misk runs Jetty with an executor pool that has no upstream Jetty thread MBeans. */
interface JettyThreads {
  fun poolSize(): Long

  fun active(): Long

  fun idle(): Long

  fun max(): Long

  fun queued(): Long
}

/**
 * Registers custom Jetty HTTP observations and the non-JMX thread-pool fallback on the application's existing SDK.
 * Standard Jetty thread, selector, and session MBeans are collected separately by upstream JMX rules.
 */
class JettyMetrics(openTelemetry: OpenTelemetry) {
  private val meter: Meter = openTelemetry.getMeter("misk.jetty")
  private val statusClassKey = AttributeKey.stringKey("jetty.response.status_class")
  private val statusAttributes = Array(5) { statusClass -> Attributes.of(statusClassKey, "${statusClass + 1}xx") }

  fun observeStatistics(statistics: JettyStatistics): AutoCloseable = register {
    counter("jetty.requests", "Requests handled", "{request}") { statistics.requests() }
    upDownCounter("jetty.requests.active", "Requests in progress", "{request}") { statistics.requestsActive() }
    gauge("jetty.requests.active.max", "Peak concurrent requests", "{request}") { statistics.requestsActiveMax() }
    doubleGauge("jetty.request.time.max", "Longest request handling time", "s") { statistics.requestTimeMaxSeconds() }
    doubleCounter("jetty.request.time", "Total request handling time", "s") { statistics.requestTimeTotalSeconds() }

    counter("jetty.dispatches", "Dispatches handled", "{dispatch}") { statistics.dispatches() }
    upDownCounter("jetty.dispatches.active", "Dispatches in progress", "{dispatch}") { statistics.dispatchesActive() }
    gauge("jetty.dispatches.active.max", "Peak concurrent dispatches", "{dispatch}") {
      statistics.dispatchesActiveMax()
    }
    doubleGauge("jetty.dispatch.time.max", "Longest dispatch handling time", "s") {
      statistics.dispatchTimeMaxSeconds()
    }
    doubleCounter("jetty.dispatch.time", "Total dispatch handling time", "s") { statistics.dispatchTimeTotalSeconds() }

    if (statistics is JettyServletStatistics) {
      counter("jetty.async.requests", "Asynchronous requests", "{request}") { statistics.asyncRequests() }
      upDownCounter("jetty.async.requests.waiting", "Asynchronous requests waiting", "{request}") {
        statistics.asyncRequestsWaiting()
      }
      gauge("jetty.async.requests.waiting.max", "Peak asynchronous requests waiting", "{request}") {
        statistics.asyncRequestsWaitingMax()
      }
      counter("jetty.async.dispatches", "Asynchronous dispatches", "{dispatch}") { statistics.asyncDispatches() }
      counter("jetty.async.expires", "Expired asynchronous requests", "{request}") { statistics.asyncExpires() }
      counter("jetty.errors", "Requests ending with an error", "{request}") { statistics.errors() }
    }

    add(
      meter
        .counterBuilder("jetty.responses")
        .setDescription("Responses by HTTP status class")
        .setUnit("{response}")
        .buildWithCallback { measurement ->
          for (statusClass in 1..5) {
            measurement.record(statistics.responses(statusClass), statusAttributes[statusClass - 1])
          }
        }
    )
    if (statistics is JettyServletStatistics) {
      counter("jetty.responses.thrown", "Responses ending in an exception", "{response}") {
        statistics.responsesThrown()
      }
    }
    counter("jetty.responses.bytes", "Response bytes reported by Jetty", "By") { statistics.responsesBytes() }
    doubleGauge("jetty.stats.uptime", "Time since Jetty statistics were reset", "s") { statistics.uptimeSeconds() }
  }

  fun observeThreadPool(pool: JettyThreads): AutoCloseable = register {
    upDownCounter("jetty.thread.count", "The current number of threads", "{thread}") { pool.poolSize() }
    upDownCounter("jetty.thread.limit", "The configured maximum number of threads in the pool", "{thread}") {
      pool.max()
    }
    upDownCounter("jetty.thread.busy.count", "The current number of busy threads", "{thread}") { pool.active() }
    upDownCounter("jetty.thread.idle.count", "The current number of idle threads", "{thread}") { pool.idle() }
    upDownCounter("jetty.thread.queue.size", "The current job queue size", "{job}") { pool.queued() }
  }

  private fun register(block: Registration.() -> Unit): AutoCloseable {
    val registration = Registration()
    try {
      registration.block()
    } catch (failure: Throwable) {
      try {
        registration.close()
      } catch (closeFailure: Throwable) {
        failure.addSuppressed(closeFailure)
      }
      throw failure
    }
    return registration
  }

  private inner class Registration : AutoCloseable {
    private val callbacks = ArrayList<AutoCloseable>()

    fun add(callback: AutoCloseable) {
      callbacks.add(callback)
    }

    fun counter(name: String, description: String, unit: String, value: () -> Long) {
      add(meter.counterBuilder(name).setDescription(description).setUnit(unit).buildWithCallback { it.record(value()) })
    }

    fun doubleCounter(name: String, description: String, unit: String, value: () -> Double) {
      add(
        meter.counterBuilder(name).ofDoubles().setDescription(description).setUnit(unit).buildWithCallback {
          it.record(value())
        }
      )
    }

    fun gauge(name: String, description: String, unit: String, value: () -> Long) {
      add(
        meter.gaugeBuilder(name).ofLongs().setDescription(description).setUnit(unit).buildWithCallback {
          it.record(value())
        }
      )
    }

    fun upDownCounter(name: String, description: String, unit: String, value: () -> Long) {
      add(
        meter.upDownCounterBuilder(name).setDescription(description).setUnit(unit).buildWithCallback {
          it.record(value())
        }
      )
    }

    fun doubleGauge(name: String, description: String, unit: String, value: () -> Double) {
      add(meter.gaugeBuilder(name).setDescription(description).setUnit(unit).buildWithCallback { it.record(value()) })
    }

    override fun close() {
      var failure: Throwable? = null
      for (index in callbacks.indices.reversed()) {
        try {
          callbacks[index].close()
        } catch (closeFailure: Throwable) {
          if (failure == null) failure = closeFailure else failure.addSuppressed(closeFailure)
        }
      }
      callbacks.clear()
      failure?.let { throw it }
    }
  }
}
