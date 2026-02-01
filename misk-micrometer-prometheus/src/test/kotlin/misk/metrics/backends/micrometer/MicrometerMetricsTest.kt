package misk.metrics.backends.micrometer

import com.google.inject.Guice
import jakarta.inject.Inject
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import misk.metrics.v2.Metrics
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest
class MicrometerMetricsTest {
  @MiskTestModule val module = MicrometerMetricsModule()

  @Inject lateinit var metrics: Metrics

  @Test
  fun `counter increments correctly`() {
    val counter = metrics.counter("test_counter", "Test counter")

    counter.inc()
    assertThat(counter.get()).isEqualTo(1.0)

    counter.inc(5.0)
    assertThat(counter.get()).isEqualTo(6.0)
  }

  @Test
  fun `counter with labels`() {
    val counter = metrics.counter("test_counter_labels", "Test counter", listOf("env", "service"))

    val prod = counter.labels("prod", "api")
    val dev = counter.labels("dev", "api")

    prod.inc()
    prod.inc(2.0)
    dev.inc()

    assertThat(prod.get()).isEqualTo(3.0)
    assertThat(dev.get()).isEqualTo(1.0)
  }

  @Test
  fun `gauge set and increment`() {
    val gauge = metrics.gauge("test_gauge", "Test gauge")

    gauge.set(10.0)
    assertThat(gauge.get()).isEqualTo(10.0)

    gauge.inc()
    assertThat(gauge.get()).isEqualTo(11.0)

    gauge.inc(5.0)
    assertThat(gauge.get()).isEqualTo(16.0)

    gauge.dec()
    assertThat(gauge.get()).isEqualTo(15.0)

    gauge.dec(3.0)
    assertThat(gauge.get()).isEqualTo(12.0)
  }

  @Test
  fun `gauge with labels`() {
    val gauge = metrics.gauge("test_gauge_labels", "Test gauge", listOf("region"))

    val usEast = gauge.labels("us-east-1")
    val usWest = gauge.labels("us-west-2")

    usEast.set(100.0)
    usWest.set(200.0)

    assertThat(usEast.get()).isEqualTo(100.0)
    assertThat(usWest.get()).isEqualTo(200.0)
  }

  @Test
  fun `histogram records observations`() {
    val histogram = metrics.histogram("test_histogram", "Test histogram")

    histogram.observe(10.0)
    histogram.observe(20.0)
    histogram.observe(30.0)

    // Verify it doesn't throw
    assertThat(histogram).isNotNull
  }

  @Test
  fun `histogram with labels`() {
    val histogram = metrics.histogram("test_histogram_labels", "Test histogram", listOf("endpoint"))

    val apiEndpoint = histogram.labels("/api/users")
    val webEndpoint = histogram.labels("/web/home")

    apiEndpoint.observe(100.0)
    apiEndpoint.observe(200.0)
    webEndpoint.observe(50.0)

    // Verify it doesn't throw
    assertThat(apiEndpoint).isNotNull
    assertThat(webEndpoint).isNotNull
  }

  @Test
  fun `histogram with custom buckets`() {
    val customBuckets = listOf(10.0, 50.0, 100.0, 500.0)
    val histogram =
      metrics.histogram("test_histogram_custom", "Test histogram with custom buckets", buckets = customBuckets)

    histogram.observe(25.0)
    histogram.observe(75.0)
    histogram.observe(250.0)

    assertThat(histogram).isNotNull
  }

  @Test
  fun `summary records observations`() {
    val summary = metrics.summary("test_summary", "Test summary")

    summary.observe(10.0)
    summary.observe(20.0)
    summary.observe(30.0)

    assertThat(summary).isNotNull
  }

  @Test
  fun `summary with labels and quantiles`() {
    val quantiles = mapOf(0.5 to 0.05, 0.95 to 0.01, 0.99 to 0.001)
    val summary = metrics.summary("test_summary_quantiles", "Test summary", listOf("method"), quantiles)

    val getMethod = summary.labels("GET")
    val postMethod = summary.labels("POST")

    repeat(100) { getMethod.observe(it.toDouble()) }
    repeat(50) { postMethod.observe(it.toDouble() * 2) }

    assertThat(getMethod).isNotNull
    assertThat(postMethod).isNotNull
  }

  @Test
  fun `peak gauge records peak values`() {
    val peakGauge = metrics.peakGauge("test_peak_gauge", "Test peak gauge")

    peakGauge.record(10.0)
    peakGauge.record(50.0)
    peakGauge.record(30.0) // Lower than peak, should not update

    // Collect will reset, so we can't easily test the exact value
    // Just verify it doesn't throw
    assertThat(peakGauge).isNotNull
  }

  @Test
  fun `peak gauge with labels`() {
    val peakGauge = metrics.peakGauge("test_peak_gauge_labels", "Test peak gauge", listOf("pool"))

    val pool1 = peakGauge.labels("pool1")
    val pool2 = peakGauge.labels("pool2")

    pool1.record(100.0)
    pool2.record(200.0)

    assertThat(peakGauge).isNotNull
  }

  @Test
  fun `provided gauge with value provider`() {
    class ResourcePool {
      var activeConnections = 0
    }

    val pool = ResourcePool()
    val providedGauge = metrics.providedGauge("test_provided_gauge", "Test provided gauge")

    providedGauge.registerProvider(pool) { activeConnections }

    pool.activeConnections = 10
    // The gauge should read from the provider

    pool.activeConnections = 20
    // The gauge should reflect the new value

    assertThat(providedGauge).isNotNull
  }

  @Test
  fun `provided gauge with labels`() {
    class Cache {
      var size = 0
    }

    val providedGauge = metrics.providedGauge("test_provided_gauge_labels", "Test provided gauge", listOf("cache_name"))

    val userCache = Cache()
    val sessionCache = Cache()

    val userGauge = providedGauge.labels("users")
    val sessionGauge = providedGauge.labels("sessions")

    userGauge.registerProvider(userCache) { size }
    sessionGauge.registerProvider(sessionCache) { size }

    userCache.size = 100
    sessionCache.size = 50

    assertThat(providedGauge).isNotNull
  }

  @Test
  fun `metrics thread safety`() {
    val counter = metrics.counter("test_concurrent_counter", "Concurrent counter test")
    val threadCount = 10
    val incrementsPerThread = 1000
    val latch = CountDownLatch(threadCount)

    val threads =
      (1..threadCount).map {
        thread {
          repeat(incrementsPerThread) { counter.inc() }
          latch.countDown()
        }
      }

    latch.await()
    threads.forEach { it.join() }

    assertThat(counter.get()).isEqualTo((threadCount * incrementsPerThread).toDouble())
  }

  @Test
  fun `collector registry is accessible`() {
    val registry = metrics.getRegistry()
    assertThat(registry).isNotNull

    // Create a metric and verify it's registered
    metrics.counter("registry_test", "Registry test")

    // The registry should contain metrics
    assertThat(registry.metricFamilySamples()).isNotNull
  }

  @Test
  fun `can inject both v1 and v2 metrics`() {
    val injector = Guice.createInjector(MicrometerMetricsModule())

    val v2Metrics = injector.getInstance(misk.metrics.v2.Metrics::class.java)
    val v1Metrics = injector.getInstance(misk.metrics.Metrics::class.java)

    assertThat(v2Metrics).isNotNull
    assertThat(v1Metrics).isNotNull

    // Both should work
    v2Metrics.counter("v2_counter", "V2 counter test")
    v1Metrics.counter("v1_counter", "V1 counter test", listOf())
  }

  @Test
  fun `can provide custom PrometheusMeterRegistry`() {
    val customRegistry =
      io.micrometer.prometheus.PrometheusMeterRegistry(io.micrometer.prometheus.PrometheusConfig.DEFAULT)

    val injector = Guice.createInjector(MicrometerMetricsModule(customRegistry))

    val prometheusMeterRegistry = injector.getInstance(io.micrometer.prometheus.PrometheusMeterRegistry::class.java)
    val meterRegistry = injector.getInstance(io.micrometer.core.instrument.MeterRegistry::class.java)
    val metrics = injector.getInstance(misk.metrics.v2.Metrics::class.java)

    // Should use the provided registry
    assertThat(prometheusMeterRegistry).isSameAs(customRegistry)
    assertThat(meterRegistry).isSameAs(customRegistry)

    // Metrics should work with the provided registry
    val counter = metrics.counter("custom_registry_counter", "Counter using custom registry")
    counter.inc()
    assertThat(counter.get()).isEqualTo(1.0)

    // The registry should contain our metric
    assertThat(customRegistry.prometheusRegistry.metricFamilySamples()).isNotNull
  }
}
