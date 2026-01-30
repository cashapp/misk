package misk.metrics.v3

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.function.ToDoubleFunction

internal class MetricsImpl(private val meterRegistry: MeterRegistry) : Metrics {
  override fun registry(): MeterRegistry = meterRegistry

  override fun counter(name: String, help: String, labelNames: List<String>) =
    CounterImpl(meterRegistry, name, help, labelNames)

  override fun gauge(name: String, help: String, labelNames: List<String>) =
    GaugeImpl(meterRegistry, name, help, labelNames, peak = false)

  override fun peakGauge(name: String, help: String, labelNames: List<String>) =
    GaugeImpl(meterRegistry, name, help, labelNames, peak = true)

  override fun providedGauge(name: String, help: String, labelNames: List<String>) =
    ProvidedGaugeImpl(meterRegistry, name, help, labelNames)

  override fun histogram(name: String, help: String, labelNames: List<String>, bucketsMs: List<Double>) =
    HistogramImpl(meterRegistry, name, help, labelNames, bucketsMs)

  override fun summary(
    name: String,
    help: String,
    labelNames: List<String>,
    quantiles: Map<Double, Double>,
    maxAgeSeconds: Long?,
  ) = SummaryImpl(meterRegistry, name, help, labelNames, quantiles, maxAgeSeconds)
}

internal fun tags(labelNames: List<String>, labelValues: Array<out String>): Iterable<Tag> {
  require(labelValues.size == labelNames.size) {
    "Expected ${labelNames.size} label values (${labelNames.joinToString()}), got ${labelValues.size}"
  }
  return labelNames.zip(labelValues.asList()).map { Tag.of(it.first, it.second) }
}

internal class CounterImpl(
  private val registry: MeterRegistry,
  private val name: String,
  private val help: String,
  private val labelNames: List<String>,
) : Metrics.Counter {
  private val children = ConcurrentHashMap<List<String>, Counter>()

  override fun labels(vararg labelValues: String) =
    object : Metrics.Counter.Child {
      private fun meter(): Counter =
        children.computeIfAbsent(labelValues.toList()) {
          Counter.builder(name).description(help).tags(tags(labelNames, labelValues)).register(registry)
        }

      override fun inc(amount: Double) {
        meter().increment(amount)
      }
    }
}

internal class GaugeImpl(
  private val registry: MeterRegistry,
  private val name: String,
  private val help: String,
  private val labelNames: List<String>,
  private val peak: Boolean,
) : Metrics.Gauge {
  private class ChildState(val value: AtomicReference<Double>)

  private val children = ConcurrentHashMap<List<String>, ChildState>()

  override fun labels(vararg labelValues: String) =
    object : Metrics.Gauge.Child {
      private val state =
        children.computeIfAbsent(labelValues.toList()) {
          val value = AtomicReference(0.0)
          val supplier =
            ToDoubleFunction<AtomicReference<Double>> { ref -> if (peak) ref.getAndSet(0.0) else ref.get() }
          Gauge.builder(name, value, supplier).description(help).tags(tags(labelNames, labelValues)).register(registry)
          ChildState(value)
        }

      override fun set(v: Double) {
        state.value.set(v)
      }

      override fun inc(amount: Double) {
        state.value.updateAndGet { it + amount }
      }

      override fun get(): Double = state.value.get()
    }
}

internal class ProvidedGaugeImpl(
  private val registry: MeterRegistry,
  private val name: String,
  private val help: String,
  private val labelNames: List<String>,
) : Metrics.ProvidedGauge {
  private val installed = ConcurrentHashMap<List<String>, Boolean>()

  override fun labels(vararg labelValues: String) =
    object : Metrics.ProvidedGauge.Child {
      private val key = labelValues.toList()

      @Volatile private var registered = false

      override fun setSupplier(supplier: () -> Double) {
        if (registered || installed.putIfAbsent(key, true) == true) {
          throw IllegalStateException("Supplier already set for $name labels=$key")
        }
        Gauge.builder(name, supplier) { it.invoke() }
          .description(help)
          .tags(tags(labelNames, labelValues))
          .register(registry)
        registered = true
      }
    }
}

internal class HistogramImpl(
  private val registry: MeterRegistry,
  private val name: String,
  private val help: String,
  private val labelNames: List<String>,
  private val bucketsMs: List<Double>,
) : Metrics.Histogram {
  private val children = ConcurrentHashMap<List<String>, DistributionSummary>()

  override fun labels(vararg labelValues: String) =
    object : Metrics.Histogram.Child {
      private fun meter(): DistributionSummary =
        children.computeIfAbsent(labelValues.toList()) {
          DistributionSummary.builder(name)
            .description(help)
            .baseUnit("milliseconds")
            .tags(tags(labelNames, labelValues))
            .serviceLevelObjectives(*bucketsMs.toDoubleArray())
            .register(registry)
        }

      override fun observe(value: Double) {
        meter().record(value)
      }

      override fun timeMs(block: () -> Unit): Double {
        val start = System.nanoTime()
        try {
          block()
        } finally {
          val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
          meter().record(elapsedMs)
          return elapsedMs
        }
      }
    }
}

internal class SummaryImpl(
  private val registry: MeterRegistry,
  private val name: String,
  private val help: String,
  private val labelNames: List<String>,
  private val quantiles: Map<Double, Double>,
  private val maxAgeSeconds: Long?,
) : Metrics.Summary {
  private val children = ConcurrentHashMap<List<String>, DistributionSummary>()

  override fun labels(vararg labelValues: String) =
    object : Metrics.Summary.Child {
      private fun meter(): DistributionSummary =
        children.computeIfAbsent(labelValues.toList()) {
          val builder = DistributionSummary.builder(name).description(help).tags(tags(labelNames, labelValues))

          if (quantiles.isNotEmpty()) {
            builder.publishPercentiles(*quantiles.keys.sorted().toDoubleArray())
          }
          if (maxAgeSeconds != null) {
            builder.distributionStatisticExpiry(Duration.ofSeconds(maxAgeSeconds))
          }
          builder.register(registry)
        }

      override fun observe(value: Double) {
        meter().record(value)
      }
    }
}
