package misk.metrics

import com.codahale.metrics.Counter
import com.codahale.metrics.Gauge
import com.codahale.metrics.Histogram
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.Timer
import com.google.common.base.Suppliers
import misk.metrics.Metrics.Companion.sanitize
import java.time.Duration
import java.util.concurrent.TimeUnit

open class MetricsScope internal constructor(
        internal val root: String,
        internal val metricRegistry: MetricRegistry
) {
    fun counter(name: String): Counter {
        return metricRegistry.counter(scopedName(name))
    }

    fun <T> gauge(name: String, f: () -> T): Gauge<T> {
        return metricRegistry.register(scopedName(name), Gauge<T> { f.invoke() })
    }

    fun <T> cachedGauge(name: String, duration: Duration, f: () -> T): Gauge<T> {
        val supplier = Suppliers.memoizeWithExpiration({ f.invoke() }, duration.toMillis(),
                TimeUnit.MILLISECONDS)
        return metricRegistry.register(scopedName(name), Gauge<T> { supplier.get() })
    }

    fun settableGauge(name: String): SettableGauge {
        return metricRegistry.register(scopedName(name), SettableGauge())
    }

    fun timer(name: String): Timer {
        return metricRegistry.timer(scopedName(name))
    }

    fun histogram(name: String): Histogram {
        return metricRegistry.histogram(scopedName(name))
    }

    fun scope(name: String, vararg names: String): MetricsScope {
        val sanitizedNames = listOf(name, *names)
                .filter { it.isNotBlank() }
                .map { sanitize(it) }
                .toTypedArray()

        return MetricsScope(MetricRegistry.name(root, *sanitizedNames), metricRegistry)
    }

    fun scopedName(name: String): String = MetricRegistry.name(root, sanitize(name))
}