package misk.concurrent

import com.google.common.util.concurrent.AbstractScheduledService
import com.netflix.concurrency.limits.MetricRegistry
import com.netflix.concurrency.limits.MetricRegistry.Counter
import com.netflix.concurrency.limits.MetricRegistry.SampleListener
import io.prometheus.client.Gauge
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import javax.inject.Inject
import javax.inject.Singleton
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.metrics.Histogram
import misk.metrics.Metrics
import io.prometheus.client.Counter as MiskCounter
import io.prometheus.client.Gauge as MiskGauge

/**
 * Adapt Misk's metrics API to Netflix's metrics API. This is primarily for use by the concurrency
 * limiter.
 *
 * There's a few factors that makes this complicated...
 *
 *  * The two APIs work quite differently for gauges. The Netflix API accepts a callback that should
 *    be invoked periodically. Misk metrics' gauges must be invoked manually. We use a scheduled
 *    executor service to update all registered gauges once per second.
 *
 *  * Labels also work differently. The Netflix API creates a new Counter, Gauge, or Distribution
 *    once for each combination of labels. The Misk API wants to create each one exactly once. This
 *    class memoizes returned objects to defend against this.
 *
 *  * The Netflix API does not expect metric names to be namespaced. In this API the [create]
 *    function provides a namespace prefix like "concurrency_limits_" to ensure namespacing.
 */
@Singleton
internal class NetflixMetricsAdapter @Inject constructor(
  private val metrics: Metrics,
) {
  private val counters = Collections.synchronizedMap(mutableMapOf<String, MiskCounter>())
  private val gauges = Collections.synchronizedMap(mutableMapOf<String, MiskGauge>())
  private val histograms = Collections.synchronizedMap(mutableMapOf<String, Histogram>())
  private val updatableGauges = Collections.synchronizedList(mutableListOf<RegisteredGauge>())

  private class RegisteredGauge(
    val supplier: Supplier<Number>,
    val gauge: Gauge.Child
  )

  fun updateGauges() {
    for (updatableGauge in updatableGauges) {
      val currentValue = updatableGauge.supplier.get()
      updatableGauge.gauge.set(currentValue.toDouble())
    }
  }

  fun create(prefix: String) : MetricRegistry {
    return object : MetricRegistry {
      override fun counter(id: String, vararg tagNameValuePairs: String): Counter {
        val labelValues = tagNameValuePairs.labelValues

        val delegate = counters.getOrPut(id) {
          val labelNames = tagNameValuePairs.labelNames
          metrics.counter(name = "${prefix}${id}", help = "-", labelNames = labelNames)
        }.labels(*labelValues)

        return Counter { delegate.inc() }
      }

      override fun gauge(id: String, supplier: Supplier<Number>, vararg tagNameValuePairs: String) {
        val labelValues = tagNameValuePairs.labelValues

        val gauge = gauges.getOrPut(id) {
          val labelNames = tagNameValuePairs.labelNames
          metrics.gauge(name = "${prefix}${id}", help = "-", labelNames = labelNames)
        }.labels(*labelValues)

        updatableGauges += RegisteredGauge(supplier, gauge)
      }

      override fun distribution(id: String, vararg tagNameValuePairs: String): SampleListener {
        val labelValues = tagNameValuePairs.labelValues

        val delegate = histograms.getOrPut(id) {
          val labelNames = tagNameValuePairs.labelNames
          metrics.histogram(name = "${prefix}${id}", help = "-", labelNames = labelNames)
        }

        return SampleListener { value ->
          delegate.record(value.toDouble(), *labelValues)
        }
      }
    }
  }

  /** The Netflix API uses alternating names and values. Names are the even elements. */
  private val Array<out String>.labelNames: List<String>
    get() = filterIndexed { index, _ -> index % 2 == 0 }

  /** Values are the odd elements. */
  private val Array<out String>.labelValues: Array<String>
    get() = filterIndexed { index, _ -> index % 2 == 1 }.toTypedArray()

  /** Publish Gauge values once per second. */
  @Singleton
  internal class UpdateGaugesService @Inject constructor(
    val adapter: NetflixMetricsAdapter,
    private val executorServiceFactory: ExecutorServiceFactory
  ) : AbstractScheduledService() {
    public override fun runOneIteration() {
      adapter.updateGauges()
    }

    override fun scheduler(): Scheduler =
      Scheduler.newFixedRateSchedule(1_000L, 1_000L, TimeUnit.MILLISECONDS)

    override fun executor() =
      executorServiceFactory.scheduled("NetflixMetricsAdapter", threadCount = 1)
  }

  companion object {
    val MODULE = object : KAbstractModule() {
      override fun configure() {
        install(ServiceModule<UpdateGaugesService>())
        bind<NetflixMetricsAdapter>()
      }
    }
  }
}
