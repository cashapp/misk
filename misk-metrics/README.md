Metrics
=======

Misk's metrics interface is a partial abstraction over Prometheus metrics.

There are two roles in the Metrics API:

 * Metrics producers: this is application code that emits metrics when certain events happen. Code
   in this role should depend on this misk-metrics module.
   
 * Metrics backends: this is infrastructure code that implements the metrics APIs used by metrics
   producers. Our current only backend is Prometheus. Backends can be in-memory only (for testing 
   and development) or they can integrate with a metrics service.

## How to Use

The `misk-metrics` module provides a simple interface for creating and using metrics in your application. You can create counters, gauges, and histograms.

```kotlin
class MyService @Inject constructor(private val metrics: Metrics) {
  // Create a counter with no labels
  private val simpleCounter = metrics.counter(
    name = "my_simple_counter",
    help = "Counts the number of operations performed"
  )

  // Create a counter with labels
  private val labeledCounter = metrics.counter(
    name = "my_labeled_counter",
    help = "Counts operations by type and status",
    labelNames = listOf("type", "status")
  )

  // Create a gauge
  private val gauge = metrics.gauge(
    name = "my_gauge",
    help = "Shows the current number of active operations",
    labelNames = listOf("type")
  )

  // Create a histogram
  private val histogram = metrics.histogram(
    name = "my_operation_duration",
    help = "Measures the duration of operations in milliseconds",
    labelNames = listOf("operation_type")
  )

  fun performOperation(type: String) {
    // Increment a simple counter
    simpleCounter.inc()

    // Increment a labeled counter
    labeledCounter.labels(type, "success").inc()

    // Set a gauge value
    gauge.labels(type).set(42.0)

    // Record a histogram value
    val startTime = System.currentTimeMillis()
    try {
      // Do work...
    } finally {
      val duration = System.currentTimeMillis() - startTime
      histogram.labels(type).observe(duration.toDouble())
    }
  }
}
```

### How to Test

To test your metrics, add the following dependency to your Gradle build file:

```kotlin
testImplementation(testFixtures(libs.miskMetrics))
```

In your tests, you can inject the `CollectorRegistry` to verify that your metrics are being recorded correctly:

```kotlin
@MiskTest
class MyServiceTest {

  @MiskTestModule val module = MiskTestingServiceModule(installFakeMetrics = true)

  @Inject private lateinit var collectorRegistry: CollectorRegistry
  @Inject private lateinit var myService: MyService

  @Test
  fun testMetrics() {
    // Perform operations that should record metrics
    myService.performOperation("read")

    // Test counter values
    assertThat(collectorRegistry.get("my_simple_counter")).isEqualTo(1.0)
    assertThat(collectorRegistry.get("my_labeled_counter", "type" to "read", "status" to "success")).isEqualTo(1.0)

    // Test gauge values
    assertThat(collectorRegistry.get("my_gauge", "type" to "read")).isEqualTo(42.0)

    // Test histogram values
    // Check count of observations
    assertThat(collectorRegistry.summaryCount("my_operation_duration", "operation_type" to "read")).isEqualTo(1.0)

    // For histograms, you can also check percentiles
    val p99 = collectorRegistry.summaryP99("my_operation_duration", "operation_type" to "read")
    assertThat(p99).isNotNull()
  }
}
```

The `CollectorRegistry` provides several extension methods for testing different types of metrics:

- `get(name, vararg labels)`: Returns the value of a counter or gauge
- `summaryCount(name, vararg labels)`: Returns the number of observations recorded in a histogram
- `summaryP99(name, vararg labels)`: Returns the 99th percentile value of a histogram
- `summaryMean(name, vararg labels)`: Returns the mean value of a histogram
- `summaryP50(name, vararg labels)`: Returns the median (50th percentile) value of a histogram

These extension methods make it easy to test that your metrics are being recorded correctly.

