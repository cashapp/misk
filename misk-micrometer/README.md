# misk-micrometer

This module adds [Micrometer](https://micrometer.io) support to Misk applications, providing a vendor-neutral facade for application metrics.

## Overview

Micrometer is to metrics what SLF4J is to logging - it provides a simple facade over different metrics backends, allowing you to instrument your code once and switch backends later without code changes.

## Features

- **Vendor-neutral metrics API**: Use Micrometer's standard APIs for counters, timers, gauges, and distribution summaries
- **Multiple backend support**: Micrometer supports Prometheus, Datadog, Graphite, InfluxDB, and many others
- **Composite registry**: Publish metrics to multiple backends simultaneously
- **Extensible**: Add custom `MeterFilter`s and `MeterBinder`s via Guice multibindings
- **Coexistence with legacy metrics**: Works alongside existing `misk-metrics` Prometheus integration

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
  implementation(project(":misk-micrometer"))
  implementation(project(":misk-micrometer-prometheus")) // For Prometheus backend
}
```

Install the modules in your service:

```kotlin
install(MicrometerModule())
install(MicrometerPrometheusModule())
```

## Usage

### Basic Metrics

Inject `MeterRegistry` and create metrics:

```kotlin
class MyService @Inject constructor(
  private val meterRegistry: MeterRegistry
) {
  private val requestCounter = Counter.builder("my.service.requests")
    .description("Total requests processed")
    .tag("service", "my-service")
    .register(meterRegistry)

  private val responseTimer = Timer.builder("my.service.response.time")
    .description("Response time")
    .publishPercentileHistogram() // For Prometheus histograms
    .register(meterRegistry)

  fun handleRequest() {
    requestCounter.increment()
    responseTimer.record {
      // Your code here
    }
  }
}
```

### Web Action Metrics

To automatically record HTTP request metrics, install the web action metrics module:

```kotlin
install(MicrometerWebActionMetricsModule())
```

This creates a timer named `http.server.requests` with tags for:
- `action`: The action name
- `status`: HTTP status code
- `caller`: Calling principal (service name or "user" or "unknown")
- `outcome`: SUCCESS, CLIENT_ERROR, SERVER_ERROR, etc.
- `exception`: Exception type (for failures)

**Note**: Don't install this alongside the legacy `MetricsInterceptor` to avoid duplicate metrics.

### Gauges

```kotlin
val queueSize = AtomicInteger(0)

Gauge.builder("queue.size", queueSize) { it.get().toDouble() }
  .description("Current queue size")
  .tag("queue", "work-queue")
  .register(meterRegistry)
```

### Distribution Summaries

```kotlin
val payloadSize = DistributionSummary.builder("payload.size")
  .description("Payload size in bytes")
  .baseUnit("bytes")
  .publishPercentileHistogram()
  .register(meterRegistry)

payloadSize.record(response.body().size.toDouble())
```

### Custom Filters

Add a `MeterFilter` via multibinding:

```kotlin
class MyMetricsModule : KAbstractModule() {
  override fun configure() {
    multibind<MeterFilter>().toInstance(
      MeterFilter.denyNameStartsWith("jvm.") // Exclude JVM metrics
    )
  }
}
```

### Custom Binders

Add JVM metrics or other common instrumentation:

```kotlin
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics

class MyMetricsModule : KAbstractModule() {
  override fun configure() {
    multibind<MeterBinder>().toInstance(JvmMemoryMetrics())
    multibind<MeterBinder>().toInstance(JvmGcMetrics())
  }
}
```

## Integration with Prometheus

The `misk-micrometer-prometheus` module integrates with Misk's existing Prometheus setup:

1. **Shared Registry**: It reuses the same `PrometheusRegistry` from `misk-metrics`
2. **Single Endpoint**: Both legacy and Micrometer metrics appear at the same `/metrics` endpoint
3. **No Configuration Changes**: Your existing Prometheus scraping continues to work

### Example

```kotlin
// In your service module
install(MetricsModule()) // Legacy metrics
install(PrometheusMetricsServiceModule()) // Prometheus endpoint
install(MicrometerModule()) // Micrometer support
install(MicrometerPrometheusModule()) // Prometheus backend for Micrometer
```

Now you can use both systems:

```kotlin
@Inject lateinit var legacyMetrics: misk.metrics.v2.Metrics
@Inject lateinit var meterRegistry: MeterRegistry

fun init() {
  // Legacy way
  val legacyCounter = legacyMetrics.counter("legacy.counter", "help")
  legacyCounter.inc()

  // Micrometer way
  val micrometerCounter = meterRegistry.counter("micrometer.counter")
  micrometerCounter.increment()

  // Both appear in Prometheus scrape at /metrics
}
```

## Migration from misk-metrics

To migrate from `misk.metrics.v2.Metrics` to Micrometer:

| Legacy API | Micrometer API |
|------------|----------------|
| `metrics.counter(name, help, labels)` | `Counter.builder(name).description(help).tags(...).register(registry)` |
| `metrics.gauge(name, help, labels)` | `Gauge.builder(name, obj, fn).tags(...).register(registry)` |
| `metrics.histogram(name, help, labels, buckets)` | `Timer.builder(name).description(help).tags(...).publishPercentileHistogram().register(registry)` |
| `metrics.summary(name, help, labels)` | `DistributionSummary.builder(name).tags(...).register(registry)` |

## Best Practices

1. **Use descriptive names**: Follow Micrometer's naming conventions (e.g., `http.server.requests`, `db.query.duration`)
2. **Add meaningful tags**: Use tags for dimensions, not metric names
3. **Enable histograms for latencies**: Use `.publishPercentileHistogram()` on timers for Prometheus
4. **Reuse meters**: Don't create new meters for each invocation; create once and reuse
5. **Don't over-tag**: Too many tag combinations create cardinality explosion

## Additional Resources

- [Micrometer Documentation](https://micrometer.io/docs)
- [Micrometer Concepts](https://micrometer.io/docs/concepts)
- [Prometheus Best Practices](https://prometheus.io/docs/practices/naming/)
