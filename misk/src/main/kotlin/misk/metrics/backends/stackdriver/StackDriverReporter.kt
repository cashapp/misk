package misk.metrics.backends.stackdriver

import com.codahale.metrics.Counter
import com.codahale.metrics.Gauge
import com.codahale.metrics.Histogram
import com.codahale.metrics.Meter
import com.codahale.metrics.Metered
import com.codahale.metrics.ScheduledReporter
import com.codahale.metrics.Timer
import com.google.api.client.util.DateTime
import com.google.api.services.monitoring.v3.model.Metric
import com.google.api.services.monitoring.v3.model.Point
import com.google.api.services.monitoring.v3.model.TimeInterval
import com.google.api.services.monitoring.v3.model.TimeSeries
import com.google.api.services.monitoring.v3.model.TypedValue
import com.google.common.base.Joiner
import misk.config.AppName
import misk.environment.InstanceMetadata
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Clock
import java.util.SortedMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject

internal class StackDriverReporter @Inject internal constructor(
    val clock: Clock,
    @AppName val appName: String,
    val instanceMetadata: InstanceMetadata,
    val sender: StackDriverSender,
    metricRegistry: com.codahale.metrics.MetricRegistry
) : ScheduledReporter(
    metricRegistry, "stack-driver", null,
    TimeUnit.MILLISECONDS, TimeUnit.MILLISECONDS
) {
  private val startTime = DateTime(clock.millis())

  override fun report(
      gauges: SortedMap<String, Gauge<Any>>?,
      counters: SortedMap<String, Counter>?,
      histograms: SortedMap<String, Histogram>?,
      meters: SortedMap<String, Meter>?,
      timers: SortedMap<String, Timer>?
  ) {
    val timeSeries = toTimeSeries(
        gauges, counters, histograms, meters,
        timers, startTime, DateTime(clock.millis())
    )
    if (timeSeries.isEmpty()) {
      return
    }

    sender.send(timeSeries)
  }

  enum class MetricKind {
    GAUGE,
    CUMULATIVE
  }

  companion object {
    private val CUSTOM_METRICS_PREFIX = "custom.googleapis.com/dw"

    private val pathJoiner = Joiner.on('/')
  }

  fun toTimeSeries(
      gauges: SortedMap<String, Gauge<Any>>?,
      counters: SortedMap<String, Counter>?,
      histograms: SortedMap<String, Histogram>?,
      meters: SortedMap<String, Meter>?,
      timers: SortedMap<String, Timer>?,
      startTime: DateTime,
      now: DateTime
  ): List<TimeSeries> {
    val timeSeries = ArrayList<TimeSeries>()
    gauges?.forEach { timeSeries.addAll(toTimeSeries(it.key, it.value, startTime, now)) }
    counters?.forEach { timeSeries.addAll(toTimeSeries(it.key, it.value, startTime, now)) }
    histograms?.forEach { timeSeries.addAll(toTimeSeries(it.key, it.value, startTime, now)) }
    timers?.forEach { timeSeries.addAll(toTimeSeries(it.key, it.value, startTime, now)) }
    meters?.forEach { timeSeries.addAll(toTimeSeries(it.key, it.value, startTime, now)) }
    return timeSeries
  }

  fun toTimeSeries(
      name: String,
      gauge: Gauge<Any>,
      startTime: DateTime,
      now: DateTime
  ): Array<TimeSeries> = arrayOf(
      timeSeries(name, gauge.value, startTime, now, "value", MetricKind.GAUGE)
  )

  fun toTimeSeries(
      name: String,
      counter: Counter,
      startTime: DateTime,
      now: DateTime
  ): Array<TimeSeries> = arrayOf(
      timeSeries(name, counter.count, startTime, now, "count", MetricKind.CUMULATIVE)
  )

  fun toTimeSeries(
      name: String,
      timer: Timer,
      startTime: DateTime,
      now: DateTime
  ): Array<TimeSeries> {
    val snapshot = timer.snapshot
    return arrayOf(
        timeSeries(
            name, convertDuration(snapshot.max.toDouble()), startTime, now, "max",
            MetricKind.GAUGE
        ),
        timeSeries(
            name, convertDuration(snapshot.min.toDouble()), startTime, now, "min",
            MetricKind.GAUGE
        ),
        timeSeries(
            name, convertDuration(snapshot.mean), startTime, now, "mean",
            MetricKind.GAUGE
        ),
        timeSeries(
            name, convertDuration(snapshot.stdDev), startTime, now, "stdDev",
            MetricKind.GAUGE
        ),
        timeSeries(
            name, convertDuration(snapshot.median), startTime, now, "p50",
            MetricKind.GAUGE
        ),
        timeSeries(
            name, convertDuration(snapshot.get75thPercentile()), startTime, now,
            "p75", MetricKind.GAUGE
        ),
        timeSeries(
            name, convertDuration(snapshot.get95thPercentile()), startTime, now,
            "p95", MetricKind.GAUGE
        ),
        timeSeries(
            name, convertDuration(snapshot.get98thPercentile()), startTime, now,
            "p98", MetricKind.GAUGE
        ),
        timeSeries(
            name, convertDuration(snapshot.get99thPercentile()), startTime, now,
            "p99", MetricKind.GAUGE
        ),
        timeSeries(
            name, convertDuration(snapshot.get999thPercentile()), startTime, now,
            "p999", MetricKind.GAUGE
        ),
        *toTimeSeries(name, timer as Metered, startTime, now)
    )
  }

  fun toTimeSeries(
      name: String,
      hist: Histogram,
      startTime: DateTime,
      now: DateTime
  ): Array<TimeSeries> {
    val snapshot = hist.snapshot
    return arrayOf(
        timeSeries(name, hist.count, startTime, now, "count", MetricKind.CUMULATIVE),
        timeSeries(name, snapshot.max.toDouble(), startTime, now, "max", MetricKind.GAUGE),
        timeSeries(name, snapshot.min.toDouble(), startTime, now, "min", MetricKind.GAUGE),
        timeSeries(name, snapshot.mean, startTime, now, "mean", MetricKind.GAUGE),
        timeSeries(name, snapshot.stdDev, startTime, now, "stdDev", MetricKind.GAUGE),
        timeSeries(name, snapshot.median, startTime, now, "p50", MetricKind.GAUGE),
        timeSeries(
            name, snapshot.get75thPercentile(), startTime, now, "p75",
            MetricKind.GAUGE
        ),
        timeSeries(
            name, snapshot.get95thPercentile(), startTime, now, "p95",
            MetricKind.GAUGE
        ),
        timeSeries(
            name, snapshot.get98thPercentile(), startTime, now, "p98",
            MetricKind.GAUGE
        ),
        timeSeries(
            name, snapshot.get99thPercentile(), startTime, now, "p99",
            MetricKind.GAUGE
        ),
        timeSeries(
            name, snapshot.get999thPercentile(), startTime, now, "p999",
            MetricKind.GAUGE
        )
    )
  }

  fun toTimeSeries(
      name: String,
      metered: Metered,
      startTime: DateTime,
      now: DateTime
  ): Array<TimeSeries> = arrayOf(
      timeSeries(name, metered.count, startTime, now, "count", MetricKind.CUMULATIVE),
      timeSeries(
          name, convertRate(metered.oneMinuteRate), startTime, now, "m1_rate",
          MetricKind.GAUGE
      ),
      timeSeries(
          name, convertRate(metered.meanRate), startTime, now, "mean_rate",
          MetricKind.GAUGE
      ),
      timeSeries(
          name, convertRate(metered.fiveMinuteRate), startTime, now, "m5_rate",
          MetricKind.GAUGE
      ),
      timeSeries(
          name, convertRate(metered.fifteenMinuteRate), startTime, now, "m15_rate",
          MetricKind.GAUGE
      )
  )

  private fun timeSeries(
      name: String,
      pointValue: Any,
      startTime: DateTime,
      now: DateTime,
      subType: String,
      metricKind: MetricKind
  ): TimeSeries = TimeSeries()
      .setMetricKind(metricKind.name)
      .setMetric(metric(type(name, subType)))
      .setPoints(listOf(point(metricKind, startTime, now, typedValue(pointValue))))

  private fun point(
      metricKind: MetricKind,
      startTime: DateTime,
      now: DateTime,
      value: TypedValue
  ): Point = Point()
      .setInterval(timeInterval(startTime, now, metricKind))
      .setValue(value)

  private fun timeInterval(
      startTime: DateTime,
      now: DateTime,
      metricKind: MetricKind
  ): TimeInterval = when (metricKind) {
    MetricKind.CUMULATIVE -> TimeInterval()
        .setStartTime(startTime.toStringRfc3339())
        .setEndTime(now.toStringRfc3339())
    MetricKind.GAUGE -> TimeInterval().setEndTime(now.toStringRfc3339())
  }

  private fun type(
      root: String,
      vararg others: String
  ) =
      pathJoiner.join(
          CUSTOM_METRICS_PREFIX,
          appName, instanceMetadata.zone, instanceMetadata.instanceName,
          root, *others
      )

  private fun metric(type: String): Metric = Metric().setType(type)

  private fun typedValue(o: Any): TypedValue = when (o) {
    is Float -> TypedValue().setDoubleValue(o.toDouble())
    is Double -> TypedValue().setDoubleValue(o.toDouble())
    is BigInteger -> TypedValue().setDoubleValue(o.toDouble())
    is BigDecimal -> TypedValue().setDoubleValue(o.toDouble())
    is Number -> TypedValue().setInt64Value(o.toLong())
    else -> TypedValue()
  }

}
