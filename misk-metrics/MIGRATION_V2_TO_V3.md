# Migrating from Misk Metrics v2 to v3

This guide helps you migrate from `misk.metrics.v2.Metrics` (Prometheus client-based) to `misk.metrics.v3.Metrics` (Micrometer-based).

## Why Migrate to v3?

- **Richer ecosystem**: Access to Micrometer's extensive meter registry backends and features
- **Better integration**: Leverage existing Micrometer tooling and community plugins
- **Consistent with modern patterns**: Aligns with industry-standard metrics libraries
- **Future-proof**: Built on actively maintained Micrometer framework
- **Shared endpoint**: Metrics from v2 and v3 can coexist during migration

## Module Setup

### v2 Setup (Old)

```kotlin
install(MetricsModule())  // or PrometheusMetricsClientModule
install(PrometheusMetricsServiceModule())
```

### v3 Setup (New)

```kotlin
// Keep existing modules for backward compatibility during migration
install(MetricsModule())
install(PrometheusMetricsServiceModule())

// Add new Micrometer modules
install(MicrometerModule())
install(MicrometerPrometheusModule())
install(MetricsV3Module())
```

**Note**: Both v2 and v3 share the same `CollectorRegistry`, so all metrics appear at the same Prometheus endpoint.

## Dependency Changes

### build.gradle.kts

```kotlin
dependencies {
  // Old v2 dependencies (can keep during migration)
  implementation(project(":misk-metrics"))
  implementation(project(":misk-prometheus"))
  
  // New v3 dependencies
  implementation(project(":misk-micrometer"))
  implementation(project(":misk-micrometer-prometheus"))
}
```

## API Migration

The v3 API closely mirrors v2, with minimal code changes required.

### Import Changes

```kotlin
// Old v2
import misk.metrics.v2.Metrics

// New v3
import misk.metrics.v3.Metrics
```

### Counter Migration

**v2 Code:**
```kotlin
@Inject lateinit var metrics: misk.metrics.v2.Metrics

val requestCounter = metrics.counter(
  name = "http_requests_total",
  help = "Total HTTP requests",
  labelNames = listOf("method", "status")
)

requestCounter.labels("GET", "200").inc()
requestCounter.labels("POST", "201").inc(5.0)
```

**v3 Code:**
```kotlin
@Inject lateinit var metrics: misk.metrics.v3.Metrics

val requestCounter = metrics.counter(
  name = "http_requests_total",
  help = "Total HTTP requests",
  labelNames = listOf("method", "status")
)

requestCounter.labels("GET", "200").inc()
requestCounter.labels("POST", "201").inc(5.0)
```

**Changes**: Only the import statement changes. API is identical.

### Gauge Migration

**v2 Code:**
```kotlin
val activeConnections = metrics.gauge(
  name = "active_connections",
  help = "Number of active connections",
  labelNames = listOf("pool")
)

activeConnections.labels("main").set(42.0)
activeConnections.labels("main").inc(5.0)
activeConnections.labels("main").dec(2.0)
```

**v3 Code:**
```kotlin
val activeConnections = metrics.gauge(
  name = "active_connections",
  help = "Number of active connections",
  labelNames = listOf("pool")
)

activeConnections.labels("main").set(42.0)
activeConnections.labels("main").inc(5.0)
activeConnections.labels("main").dec(2.0)
```

**Changes**: API is identical.

### PeakGauge Migration

**v2 Code:**
```kotlin
val peakMemory = metrics.peakGauge(
  name = "peak_memory_bytes",
  help = "Peak memory usage",
  labelNames = listOf("region")
)

peakMemory.labels("us-west").set(1024.0)
```

**v3 Code:**
```kotlin
val peakMemory = metrics.peakGauge(
  name = "peak_memory_bytes",
  help = "Peak memory usage",
  labelNames = listOf("region")
)

peakMemory.labels("us-west").set(1024.0)
```

**Changes**: API is identical.

### ProvidedGauge Migration

**v2 Code:**
```kotlin
val queueSize = metrics.providedGauge(
  name = "queue_size",
  help = "Current queue size",
  labelNames = listOf("queue_name")
)

queueSize.labels("tasks").setSupplier { taskQueue.size().toDouble() }
```

**v3 Code:**
```kotlin
val queueSize = metrics.providedGauge(
  name = "queue_size",
  help = "Current queue size",
  labelNames = listOf("queue_name")
)

queueSize.labels("tasks").setSupplier { taskQueue.size().toDouble() }
```

**Changes**: API is identical.

### Histogram Migration

**v2 Code:**
```kotlin
val requestDuration = metrics.histogram(
  name = "http_request_duration_ms",
  help = "HTTP request duration in milliseconds",
  labelNames = listOf("endpoint"),
  buckets = defaultBuckets  // or customBuckets
)

requestDuration.labels("/api/users").observe(125.0)
```

**v3 Code:**
```kotlin
val requestDuration = metrics.histogram(
  name = "http_request_duration_ms",
  help = "HTTP request duration in milliseconds",
  labelNames = listOf("endpoint"),
  bucketsMs = defaultBuckets  // Note: parameter renamed to bucketsMs
)

requestDuration.labels("/api/users").observe(125.0)
```

**Changes**: 
- Parameter renamed from `buckets` to `bucketsMs` to emphasize units
- Under the hood: uses Micrometer's `DistributionSummary` with `serviceLevelObjectives()`

### Histogram with Timing Helper

**v3 New Feature:**
```kotlin
val elapsed = requestDuration.labels("/api/users").timeMs {
  // Your code here
  processRequest()
}
// Automatically records elapsed time in milliseconds
```

**Note**: This convenience method wasn't available in v2.

### Summary Migration

**v2 Code:**
```kotlin
val responseSizes = metrics.summary(
  name = "http_response_size_bytes",
  help = "HTTP response sizes",
  labelNames = listOf("endpoint"),
  quantiles = mapOf(0.5 to 0.05, 0.95 to 0.01, 0.99 to 0.001),
  maxAgeSeconds = 600
)

responseSizes.labels("/api/data").observe(1024.0)
```

**v3 Code:**
```kotlin
val responseSizes = metrics.summary(
  name = "http_response_size_bytes",
  help = "HTTP response sizes",
  labelNames = listOf("endpoint"),
  quantiles = mapOf(0.5 to 0.05, 0.95 to 0.01, 0.99 to 0.001),
  maxAgeSeconds = 600
)

responseSizes.labels("/api/data").observe(1024.0)
```

**Changes**: API is identical.

## Default Values

Both v2 and v3 share the same default constants:

```kotlin
import misk.metrics.v3.defaultBuckets
import misk.metrics.v3.defaultSparseBuckets
import misk.metrics.v3.defaultQuantiles

// These are the same values as v2
// defaultBuckets: 58 buckets from 1ms to 1hr
// defaultSparseBuckets: 21 buckets from 1ms to 8m
// defaultQuantiles: p50, p75, p95, p99, p999
```

## Common Migration Patterns

### 1. Incremental Migration

Migrate one metric at a time while keeping the application running:

```kotlin
class MyService @Inject constructor(
  private val metricsV2: misk.metrics.v2.Metrics,
  private val metricsV3: misk.metrics.v3.Metrics
) {
  // Old metric (can remove after migration)
  private val oldCounter = metricsV2.counter("requests_total", "Total requests")
  
  // New metric (running in parallel)
  private val newCounter = metricsV3.counter("requests_total_v3", "Total requests")
  
  fun handleRequest() {
    oldCounter.labels().inc()  // Keep during migration
    newCounter.labels().inc()  // New metric
  }
}
```

After validating the new metric works correctly, remove the old one and rename `requests_total_v3` to `requests_total`.

### 2. Factory Pattern

If you use a factory to create metrics:

```kotlin
// Old v2
class MetricsFactory @Inject constructor(
  private val metrics: misk.metrics.v2.Metrics
) {
  fun createCounter(name: String) = metrics.counter(name, "")
}

// New v3 - just change the type
class MetricsFactory @Inject constructor(
  private val metrics: misk.metrics.v3.Metrics  // Change here
) {
  fun createCounter(name: String) = metrics.counter(name, "")
}
```

### 3. Testing

**v2 Test Setup:**
```kotlin
@MiskTest
class MyTest {
  @MiskTestModule
  val module = TestModule()
  
  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MetricsModule())
    }
  }
}
```

**v3 Test Setup:**
```kotlin
@MiskTest
class MyTest {
  @MiskTestModule
  val module = TestModule()
  
  class TestModule : KAbstractModule() {
    override fun configure() {
      bind<MeterRegistry>().toInstance(SimpleMeterRegistry())
      install(MetricsV3Module())
    }
  }
}
```

## Important Differences

### 1. Histogram vs Summary Semantics

- **v2**: `histogram()` created a Prometheus `Summary` (for legacy reasons)
- **v3**: `histogram()` creates a Micrometer `DistributionSummary` with SLO buckets (true histogram behavior)
- **v3**: `summary()` creates a `DistributionSummary` with percentiles (similar to v2 histogram)

**Migration tip**: Most v2 `histogram()` usages should migrate to v3 `histogram()` with `bucketsMs`. If you specifically need percentile summaries, use v3 `summary()`.

### 2. PeakGauge Reset Behavior

- **v2**: Resets exactly once per Prometheus scrape
- **v3**: May reset multiple times during a scrape if Micrometer reads the gauge multiple times

**Impact**: Generally minimal, but peak values might be slightly lower. If strict once-per-scrape is critical, stay on v2 for that metric.

### 3. Registry Access

**v2:**
```kotlin
val registry: CollectorRegistry = metrics.getRegistry()
```

**v3:**
```kotlin
val registry: MeterRegistry = metrics.registry()
```

## Validation During Migration

1. **Run both v2 and v3 in parallel** with different metric names
2. **Compare values** in Prometheus/Grafana to ensure equivalence
3. **Update dashboards** to use new metric names
4. **Update alerts** to use new metric names
5. **Remove v2 metrics** after validation period

## Rollback Plan

If issues arise:

1. **Keep v2 modules installed** during migration
2. **Use feature flags** to toggle between v2 and v3 if needed
3. **Monitor metrics** closely after switching
4. Can easily revert by changing injection type back to v2

## Complete Example

**Before (v2):**
```kotlin
class ApiService @Inject constructor(
  private val metrics: misk.metrics.v2.Metrics
) {
  private val requestCounter = metrics.counter(
    "api_requests_total",
    "Total API requests",
    listOf("method", "endpoint", "status")
  )
  
  private val requestDuration = metrics.histogram(
    "api_request_duration_ms",
    "API request duration",
    listOf("endpoint"),
    defaultBuckets
  )
  
  fun handleRequest(endpoint: String, method: String): Response {
    val start = System.nanoTime()
    try {
      val response = processRequest(endpoint, method)
      val duration = (System.nanoTime() - start) / 1_000_000.0
      requestDuration.labels(endpoint).observe(duration)
      requestCounter.labels(method, endpoint, response.status.toString()).inc()
      return response
    } catch (e: Exception) {
      requestCounter.labels(method, endpoint, "500").inc()
      throw e
    }
  }
}
```

**After (v3):**
```kotlin
class ApiService @Inject constructor(
  private val metrics: misk.metrics.v3.Metrics  // Change type
) {
  private val requestCounter = metrics.counter(
    "api_requests_total",
    "Total API requests",
    listOf("method", "endpoint", "status")
  )
  
  private val requestDuration = metrics.histogram(
    "api_request_duration_ms",
    "API request duration",
    listOf("endpoint"),
    bucketsMs = defaultBuckets  // Parameter name changed
  )
  
  fun handleRequest(endpoint: String, method: String): Response {
    try {
      // Can use new timeMs helper
      val response = requestDuration.labels(endpoint).timeMs {
        processRequest(endpoint, method)
      }
      requestCounter.labels(method, endpoint, response.status.toString()).inc()
      return response
    } catch (e: Exception) {
      requestCounter.labels(method, endpoint, "500").inc()
      throw e
    }
  }
}
```

## Getting Help

- **Micrometer docs**: https://micrometer.io/docs
- **Misk Slack**: #misk channel
- **Questions**: Ask in your team's channel or open an issue

## Summary

Migration from v2 to v3 is straightforward:

1. ✅ Add new module dependencies
2. ✅ Install Micrometer modules alongside existing modules
3. ✅ Change injection type from `v2.Metrics` to `v3.Metrics`
4. ✅ Update `buckets` to `bucketsMs` in histogram calls
5. ✅ Test and validate
6. ✅ Clean up v2 metrics after migration complete

The API is designed to be nearly identical, making migration a mostly mechanical process.
