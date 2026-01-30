package misk.micrometer

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import jakarta.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = false)
class MicrometerModuleTest {
  @MiskTestModule val module = MicrometerModule()

  @Inject lateinit var meterRegistry: MeterRegistry
  @Inject lateinit var compositeMeterRegistry: CompositeMeterRegistry

  @Test
  fun `provides meter registry`() {
    assertThat(meterRegistry).isNotNull
    assertThat(meterRegistry).isInstanceOf(CompositeMeterRegistry::class.java)
  }

  @Test
  fun `composite registry is same as meter registry`() {
    assertThat(meterRegistry).isSameAs(compositeMeterRegistry)
  }

  @Test
  fun `can create and increment counters`() {
    val counter =
      Counter.builder("test.counter").description("A test counter").tag("env", "test").register(meterRegistry)

    counter.increment()
    counter.increment(5.0)

    assertThat(counter.count()).isEqualTo(6.0)
  }

  @Test
  fun `can create and record timers`() {
    val timer = Timer.builder("test.timer").description("A test timer").tag("operation", "test").register(meterRegistry)

    timer.record(100.milliseconds.toJavaDuration())
    timer.record(200.milliseconds.toJavaDuration())

    assertThat(timer.count()).isEqualTo(2)
    assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(300.0)
  }

  @Test
  fun `can create gauges`() {
    val value = mutableListOf(1.0)

    meterRegistry.gauge("test.gauge", listOf(), value) { it[0] }

    val gauge = meterRegistry.find("test.gauge").gauge()
    assertThat(gauge).isNotNull
    assertThat(gauge!!.value()).isEqualTo(1.0)

    value[0] = 42.0
    assertThat(gauge.value()).isEqualTo(42.0)
  }

  @Test
  fun `meters have tags`() {
    val counter =
      Counter.builder("tagged.counter").tag("region", "us-west").tag("service", "misk-test").register(meterRegistry)

    counter.increment()

    val foundCounter =
      meterRegistry.find("tagged.counter").tag("region", "us-west").tag("service", "misk-test").counter()

    assertThat(foundCounter).isNotNull
    assertThat(foundCounter!!.count()).isEqualTo(1.0)
  }
}
