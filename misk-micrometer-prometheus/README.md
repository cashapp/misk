# misk-micrometer-prometheus

This module provides a Micrometer-backed implementation of Misk v2 Metrics using Prometheus as the backend.

## Why use this module?

This module uses [Micrometer](https://micrometer.io/) with Prometheus instead of the raw Prometheus client. Micrometer provides:
- A vendor-neutral metrics facade
- Better abstractions for common metrics patterns
- Support for multiple backends beyond Prometheus
- More sophisticated metric transformations

## Migration from misk-metrics

This module implements the exact same `misk.metrics.v2.Metrics` interface as `misk-metrics`, which means **zero code changes are required at callsites**. You only need to swap the Guice module.

### Before (using raw Prometheus)

```kotlin
install(MetricsModule())
```

### After (using Micrometer with Prometheus)

```kotlin
install(MicrometerMetricsModule())
```

That's it! All your existing metrics code will work without any changes:

```kotlin
@Inject lateinit var metrics: misk.metrics.v2.Metrics

fun recordMetrics() {
  val counter = metrics.counter("my_counter", "Description")
  counter.inc()
  
  val gauge = metrics.gauge("my_gauge", "Description", listOf("label1", "label2"))
  gauge.labels("value1", "value2").set(100.0)
  
  val histogram = metrics.histogram("my_histogram", "Description")
  histogram.observe(42.0)
}
```

## Features

- ✅ Full compatibility with `misk.metrics.v2.Metrics` interface
- ✅ No changes required to existing callsites or imports
- ✅ Supports all metric types: Counter, Gauge, Histogram, Summary, PeakGauge, ProvidedGauge
- ✅ Thread-safe operations
- ✅ Label/tag support
- ✅ Custom bucket and quantile configuration
- ✅ Access to both Micrometer MeterRegistry and Prometheus CollectorRegistry

## Metric Types

### Counter
```kotlin
val counter = metrics.counter("requests_total", "Total requests", listOf("endpoint"))
counter.labels("/api/users").inc()
```

### Gauge
```kotlin
val gauge = metrics.gauge("active_connections", "Active connections")
gauge.set(42.0)
gauge.inc(5.0)
gauge.dec(2.0)
```

### Histogram
```kotlin
val histogram = metrics.histogram(
  "request_duration_ms",
  "Request duration in milliseconds",
  buckets = listOf(10.0, 50.0, 100.0, 500.0, 1000.0)
)
histogram.observe(123.5)
```

### Summary
```kotlin
val summary = metrics.summary(
  "response_size_bytes",
  "Response size in bytes",
  quantiles = mapOf(0.5 to 0.05, 0.95 to 0.01, 0.99 to 0.001)
)
summary.observe(1024.0)
```

### PeakGauge
```kotlin
val peakGauge = metrics.peakGauge("max_memory_used", "Peak memory usage")
peakGauge.record(512.0)
```

### ProvidedGauge
```kotlin
class ConnectionPool {
  var activeConnections = 0
}

val pool = ConnectionPool()
val providedGauge = metrics.providedGauge("pool_connections", "Active pool connections")
providedGauge.registerProvider(pool) { activeConnections }
```

## Testing

For testing, you can use the same module:

```kotlin
@MiskTest
class MyServiceTest {
  @MiskTestModule val module = MicrometerMetricsModule()
  
  @Inject lateinit var metrics: misk.metrics.v2.Metrics
  
  @Test
  fun testMetrics() {
    val counter = metrics.counter("test_counter", "Test counter")
    counter.inc()
    assertThat(counter.get()).isEqualTo(1.0)
  }
}
```

## Dependencies

Add this to your `build.gradle.kts`:

```kotlin
dependencies {
  implementation(project(":misk-micrometer-prometheus"))
}
```

## Implementation Details

The module uses:
- Micrometer Core for the metrics facade
- Micrometer Prometheus Registry for Prometheus integration
- The existing Prometheus client for compatibility with the Misk v2 Metrics interface

The implementation provides Micrometer's `PrometheusMeterRegistry.prometheusRegistry` as the `CollectorRegistry`, which allows both Micrometer meters and direct Prometheus metrics to coexist and be scraped from the same endpoint. This means:

1. Existing Misk v2 Metrics calls (counter, gauge, histogram, etc.) continue to create Prometheus client metrics
2. You can also inject `MeterRegistry` and use Micrometer APIs directly
3. All metrics are exported through the same Prometheus scrape endpoint
4. No incompatibility between the two approaches - they share the same registry

This design provides maximum flexibility: use existing Misk metrics APIs where they work well, and gradually adopt Micrometer APIs where they provide better abstractions.
