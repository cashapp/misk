# misk-micrometer-prometheus

This module provides Micrometer-based Prometheus metrics support for Misk applications, including a Micrometer implementation of the Misk v2 Metrics API.

## Features

- **Micrometer Prometheus Integration**: Exports metrics to Prometheus using Micrometer's PrometheusMeterRegistry
- **Shared CollectorRegistry**: Shares the same Prometheus CollectorRegistry as misk-metrics for unified metrics export
- **v2 Metrics API Implementation**: Drop-in Micrometer-backed implementation of `misk.metrics.v2.Metrics`
- **Zero Code Changes**: Migrate from raw Prometheus to Micrometer without changing application code

## Usage

### Basic Micrometer Support

To add Micrometer Prometheus support to your application:

```kotlin
install(MicrometerModule())
install(MicrometerPrometheusModule())
```

This creates a `PrometheusMeterRegistry` that shares the CollectorRegistry with existing misk-metrics, allowing both to export through the same endpoint.

### Micrometer-backed v2 Metrics

To use Micrometer as the backend for the Misk v2 Metrics API:

```kotlin
install(MicrometerModule())
install(MicrometerPrometheusModule())
install(MicrometerMetricsModule())
```

Your existing code continues to work without changes:

```kotlin
class MyService @Inject constructor(
  private val metrics: Metrics  // misk.metrics.v2.Metrics
) {
  private val counter = metrics.counter("my_counter", "help text")
  private val histogram = metrics.histogram("my_histogram", "help text")
  
  fun doWork() {
    counter.inc()
    histogram.observe(123.0)
  }
}
```

## Migration

See [MIGRATION.md](MIGRATION.md) for detailed migration instructions from raw Prometheus to Micrometer.

## Components

### MicrometerPrometheusModule

Binds a `PrometheusMeterRegistry` that:
- Shares the CollectorRegistry with misk-metrics
- Adds itself to the `CompositeMeterRegistry`
- Allows unified metrics export

### MicrometerMetricsModule  

Provides a Micrometer-backed implementation of `misk.metrics.v2.Metrics` that:
- Implements the full v2 Metrics API
- Uses Micrometer for metric registration and recording
- Exports through the shared Prometheus CollectorRegistry
- Supports all metric types: Counter, Gauge, Histogram, Summary, PeakGauge, ProvidedGauge

### MicrometerMetrics

The core implementation class that wraps:
- `MeterRegistry` for Micrometer metric creation
- `CollectorRegistry` for Prometheus export compatibility

## Benefits

- **Performance**: Leverage Micrometer's optimized metric collection
- **Flexibility**: Easy to add additional metric backends (DataDog, Stackdriver, etc.)
- **Compatibility**: Works alongside existing misk-metrics code
- **Future-proof**: Built on industry-standard Micrometer library

## Dependencies

- `misk-micrometer`: Core Micrometer integration
- `misk-metrics`: Misk metrics abstractions
- `io.micrometer:micrometer-registry-prometheus`: Micrometer Prometheus registry
- `io.prometheus:simpleclient`: Prometheus Java client
