import jakarta.inject.Inject
import misk.metrics.v2.FakeMetrics
import misk.metrics.v2.FakeMetricsModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.jetty.MeasuredWindowRateControl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = false)
class MeasuredWindowRateControlTest {

  @MiskTestModule val module = FakeMetricsModule()
  @Inject lateinit var metrics: FakeMetrics

  @Test
  fun `allows events when under maxEvents limit`() {
    val rateControl = MeasuredWindowRateControl(metrics, maxEvents = 5)

    repeat(5) {
      assertThat(rateControl.onEvent("test")).isTrue()
    }
  }

  @Test
  fun `blocks events when over maxEvents limit`() {
    val rateControl = MeasuredWindowRateControl(metrics, maxEvents = 2)

    assertThat(rateControl.onEvent("test1")).isTrue()
    assertThat(rateControl.onEvent("test2")).isTrue()
    assertThat(rateControl.onEvent("test3")).isFalse()
    assertThat(rateControl.onEvent("test4")).isFalse()
  }

  @Test
  fun `allows unlimited events when maxEvents is -1`() {
    val rateControl = MeasuredWindowRateControl(metrics, maxEvents = -1)

    repeat(100) {
      assertThat(rateControl.onEvent("test")).isTrue()
    }
  }

  @Test
  fun `window sliding allows events after time passes`() {
    val rateControl = MeasuredWindowRateControl(metrics, maxEvents = 2)

    assertThat(rateControl.onEvent("test1")).isTrue()
    assertThat(rateControl.onEvent("test2")).isTrue()
    assertThat(rateControl.onEvent("test3")).isFalse()

    Thread.sleep(1100)

    assertThat(rateControl.onEvent("test4")).isTrue()
    assertThat(rateControl.onEvent("test5")).isTrue()
    assertThat(rateControl.onEvent("test6")).isFalse()
  }
}

