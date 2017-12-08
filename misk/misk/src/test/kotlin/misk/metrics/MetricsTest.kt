package misk.metrics

import com.google.common.truth.Truth.assertThat
import com.google.inject.Guice
import org.junit.Test
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class MetricsTest {
  @Test
  fun counters() {
    val metrics = buildMetrics()
    metrics.counter("my_counter").inc(100)
    assertThat(metrics.counters).containsKey("my_counter")
    assertThat(metrics.counters["my_counter"]!!.count).isEqualTo(100)
    metrics.counter("my_counter").inc(24)
    assertThat(metrics.counters["my_counter"]!!.count).isEqualTo(124)
  }

  @Test
  fun timers() {
    val metrics = buildMetrics()
    metrics.timer("my_timer").update(10, TimeUnit.SECONDS)
    assertThat(metrics.timers).containsKey("my_timer")
    assertThat(metrics.timers["my_timer"]!!.count).isEqualTo(1)
    metrics.timer("my_timer").update(23, TimeUnit.SECONDS)
    assertThat(metrics.timers["my_timer"]!!.count).isEqualTo(2)
  }

  @Test
  fun gauges() {
    val metrics = buildMetrics()
    val n = AtomicLong()

    metrics.gauge("my_gauge", { n.get() })
    assertThat(metrics.gauges).containsKey("my_gauge")
    assertThat(metrics.gauges["my_gauge"]!!.value).isEqualTo(0)
    n.set(254)
    assertThat(metrics.gauges["my_gauge"]!!.value).isEqualTo(254)
  }

  @Test
  fun settableGauges() {
    val metrics = buildMetrics()
    val gauge = metrics.settableGauge("my_gauge")

    assertThat(metrics.gauges).containsKey("my_gauge")
    assertThat(metrics.gauges["my_gauge"]!!.value).isEqualTo(0)
    gauge.value.set(254)
    assertThat(metrics.gauges["my_gauge"]!!.value).isEqualTo(254)
  }

  @Test
  fun cacheableGauges() {
    val metrics = buildMetrics()
    val n = AtomicLong()

    metrics.cachedGauge("my_gauge", Duration.ofMillis(100), n::get)
    assertThat(metrics.gauges).containsKey("my_gauge")
    assertThat(metrics.gauges["my_gauge"]!!.value).isEqualTo(0)
    n.set(254)
    assertThat(metrics.gauges["my_gauge"]!!.value).isEqualTo(0)
    Thread.sleep(120)
    assertThat(metrics.gauges["my_gauge"]!!.value).isEqualTo(254)
  }

  @Test
  fun scoping() {
    val metrics = buildMetrics()
    metrics.scope("   \t", "foo_zed", "", "bar_zed").counter("my_counter").inc(100)
    assertThat(metrics.counters).containsKey("foo_zed.bar_zed.my_counter")
  }

  fun buildMetrics(): Metrics {
    val injector = Guice.createInjector(MetricsModule())
    return injector.getInstance(Metrics::class.java)
  }
}
