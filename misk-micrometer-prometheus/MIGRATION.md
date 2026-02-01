# Migrating to Micrometer-backed Misk v2 Metrics

This guide explains how to migrate your Misk application from the raw Prometheus implementation of v2 Metrics to the new Micrometer-backed implementation.

## Overview

The new `MicrometerMetricsModule` provides a Micrometer-based implementation of the `misk.metrics.v2.Metrics` interface while maintaining complete API compatibility. This allows you to leverage Micrometer's features (like additional metric backends, automatic instrumentation, and better performance) without changing any of your existing metrics code.

## Benefits

- **Multiple Backends**: Easily export metrics to multiple systems (Prometheus, DataDog, Stackdriver, etc.)
- **Better Performance**: Micrometer's optimized metric collection
- **Additional Features**: Metric filters, binders, and automatic JVM/system metrics
- **Future-Proof**: Aligns with industry-standard metrics library

## Migration Steps

### 1. Add Dependencies

The `misk-micrometer-prometheus` module is already available in your project. No build file changes needed if you're using the Misk BOM.

### 2. Update Module Installation

**Before** (using raw Prometheus):
```kotlin
install(MetricsModule())
// or
install(PrometheusMetricsServiceModule(config.prometheus))
```

**After** (using Micrometer):
```kotlin
install(MicrometerModule())
install(MicrometerPrometheusModule()) 
install(MicrometerMetricsModule())
// optionally, if you need the metrics service endpoint:
install(PrometheusMetricsServiceModule(config.prometheus))
```

### 3. No Code Changes Required

Your existing code using `misk.metrics.v2.Metrics` continues to work without any changes:

```kotlin
class MyService @Inject constructor(
  private val metrics: Metrics  // This is misk.metrics.v2.Metrics
) {
  private val requestCounter = metrics.counter(
    "my_service_requests",
    "Number of requests",
    listOf("status")
  )
  
  private val requestDuration = metrics.histogram(
    "my_service_duration_ms",
    "Request duration in milliseconds",
    listOf("endpoint")
  )
  
  fun handleRequest() {
    requestCounter.labels("success").inc()
    // ... existing code ...
  }
}
```

No imports need to change. No metric creation calls need to change.

### 4. Verify Metrics Export

After migration, verify your Prometheus endpoint still exposes metrics correctly:

```bash
curl http://localhost:8080/metrics
```

You should see all your existing metrics plus potentially some new automatic metrics from Micrometer.

## Advanced: Adding Additional Metric Backends

One benefit of Micrometer is the ability to export to multiple backends simultaneously:

```kotlin
// Add DataDog support
install(MicrometerModule())
install(MicrometerPrometheusModule())
install(MicrometerDatadogModule(config.datadog))
install(MicrometerMetricsModule())
```

Your code using `misk.metrics.v2.Metrics` will automatically export to all configured backends.

## Compatibility

- ✅ All `misk.metrics.v2.Metrics` methods work identically
- ✅ All existing interceptors continue to work
- ✅ Prometheus export format is identical
- ✅ Existing dashboards and monitors continue to work
- ✅ Both v1 (`misk.metrics.Metrics`) and v2 APIs are supported

## Rollback

If you need to rollback, simply revert the module installation:

```kotlin
// Revert to:
install(MetricsModule())
```

No code changes are needed to rollback since the API is identical.

## Testing

In tests, use the same modules:

```kotlin
@MiskTest(startService = true)
class MyServiceTest {
  @MiskTestModule
  val module = TestModule()
  
  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(MicrometerModule())
      install(MicrometerPrometheusModule())
      install(MicrometerMetricsModule())
    }
  }
}
```

## Questions?

- **Q: Do I need to change my metric names?**  
  A: No, all metric names remain the same.

- **Q: Will my dashboards break?**  
  A: No, the Prometheus export format is identical.

- **Q: Can I use both implementations?**  
  A: No, install only one metrics module at a time.

- **Q: What about the v1 `misk.metrics.Metrics` API?**  
  A: It's supported through `MicrometerMetricsModule` which binds both v1 and v2.

- **Q: Do I get automatic JVM metrics?**  
  A: Yes, if you install `MicrometerModule` with appropriate binders.
