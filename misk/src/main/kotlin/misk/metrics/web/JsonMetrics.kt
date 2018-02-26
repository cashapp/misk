package misk.metrics.web

import com.codahale.metrics.Counter
import com.codahale.metrics.Gauge
import com.codahale.metrics.Histogram
import com.codahale.metrics.Snapshot
import com.codahale.metrics.Timer
import misk.metrics.Metrics
import java.util.concurrent.TimeUnit

data class JsonCounter(val count: Long) {
  constructor(counter: Counter) : this(counter.count)
}

data class JsonGauge<T>(val value: T) {
  constructor(gauge: Gauge<T>) : this(gauge.value)
}

data class JsonTimer(
  val count: Long,
  val oneMinuteRate: Double,
  val fiveMinuteRate: Double,
  val fifteenMinuteRate: Double,
  val meanRate: Double,
  val snapshot: JsonHistogramSnapshot
) {
  constructor(timer: Timer) : this(
      timer.count,
      timer.oneMinuteRate * RATE_FACTOR,
      timer.fiveMinuteRate * RATE_FACTOR,
      timer.fifteenMinuteRate * RATE_FACTOR,
      timer.meanRate * RATE_FACTOR,
      JsonHistogramSnapshot(timer.snapshot, DURATION_FACTOR)
  )
}

data class JsonHistogramSnapshot(
  val min: Double,
  val max: Double,
  val mean: Double,
  val stdDev: Double,
  val p50: Double,
  val p75: Double,
  val p95: Double,
  val p98: Double,
  val p99: Double,
  val p999: Double
) {
  constructor(snapshot: Snapshot, conversionFactor: Double = 1.0) : this(
      min = snapshot.min.toDouble() * conversionFactor,
      max = snapshot.max.toDouble() * conversionFactor,
      mean = snapshot.mean * conversionFactor,
      stdDev = snapshot.stdDev * conversionFactor,
      p50 = snapshot.median * conversionFactor,
      p75 = snapshot.get75thPercentile() * conversionFactor,
      p95 = snapshot.get95thPercentile() * conversionFactor,
      p98 = snapshot.get98thPercentile() * conversionFactor,
      p99 = snapshot.get99thPercentile() * conversionFactor,
      p999 = snapshot.get999thPercentile() * conversionFactor)
}

data class JsonHistogram(val count: Long, val snapshot: JsonHistogramSnapshot) {
  constructor(hist: Histogram) : this(hist.count, JsonHistogramSnapshot(hist.snapshot))
}

data class JsonMetrics(
  val counters: Map<String, JsonCounter>,
  val timers: Map<String, JsonTimer>,
  val histograms: Map<String, JsonHistogram>,
  val gauges: Map<String, JsonGauge<Any>>
) {
  constructor(metrics: Metrics) : this(
      metrics.counters.map { it.key to JsonCounter(it.value) }.toMap(),
      metrics.timers.map { it.key to JsonTimer(it.value) }.toMap(),
      metrics.histograms.map { it.key to JsonHistogram(it.value) }.toMap(),
      metrics.gauges.map { it.key to JsonGauge(it.value) }.toMap()
  )
}

private val RATE_FACTOR = 1.0 / TimeUnit.MILLISECONDS.toNanos(1)
private val DURATION_FACTOR = 1.0 / TimeUnit.MILLISECONDS.toNanos(1)

