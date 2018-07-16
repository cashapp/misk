package misk.metrics

import misk.MiskModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

@MiskTest
class MetricsTest {
  @MiskTestModule
  val module = MiskModule()

  @Inject lateinit var metrics: Metrics

  @Test
  fun counters() {
    metrics.counter("my_counter").inc(100)
    assertThat(metrics.counters).containsKey("my_counter")
    assertThat(metrics.counters["my_counter"]!!.count).isEqualTo(100)
    metrics.counter("my_counter").inc(24)
    assertThat(metrics.counters["my_counter"]!!.count).isEqualTo(124)
  }

  @Test
  fun timers() {
    metrics.timer("my_timer").update(10, TimeUnit.SECONDS)
    assertThat(metrics.timers).containsKey("my_timer")
    assertThat(metrics.timers["my_timer"]!!.count).isEqualTo(1)
    metrics.timer("my_timer").update(23, TimeUnit.SECONDS)
    assertThat(metrics.timers["my_timer"]!!.count).isEqualTo(2)
  }

  @Test
  fun gauges() {
    val n = AtomicLong()

    metrics.gauge("my_gauge", { n.get() })
    assertThat(metrics.gauges).containsKey("my_gauge")
    assertThat(metrics.gauges["my_gauge"]!!.value).isEqualTo(0L)
    n.set(254)
    assertThat(metrics.gauges["my_gauge"]!!.value).isEqualTo(254L)
  }

  @Test
  fun settableGauges() {
    val gauge = metrics.settableGauge("my_gauge")

    assertThat(metrics.gauges).containsKey("my_gauge")
    assertThat(metrics.gauges["my_gauge"]!!.value).isEqualTo(0L)
    gauge.value.set(254)
    assertThat(metrics.gauges["my_gauge"]!!.value).isEqualTo(254L)
  }

  @Test
  fun cacheableGauges() {
    val n = AtomicLong()

    metrics.cachedGauge("my_gauge", Duration.ofMillis(100), n::get)
    assertThat(metrics.gauges).containsKey("my_gauge")
    assertThat(metrics.gauges["my_gauge"]!!.value).isEqualTo(0L)
    n.set(254)
    assertThat(metrics.gauges["my_gauge"]!!.value).isEqualTo(0L)
    Thread.sleep(120)
    assertThat(metrics.gauges["my_gauge"]!!.value).isEqualTo(254L)
  }

  @Test
  fun scoping() {
    metrics.scope("   \t", "foo_zed", "", "bar_zed").counter("my_counter").inc(100)
    assertThat(metrics.counters).containsKey("foo_zed.bar_zed.my_counter")
  }
}