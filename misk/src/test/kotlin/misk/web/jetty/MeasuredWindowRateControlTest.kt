import jakarta.inject.Inject
import misk.metrics.v2.FakeMetrics
import misk.metrics.v2.FakeMetricsModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.WebConfig
import misk.web.jetty.MeasuredWindowRateControl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@MiskTest(startService = false)
class MeasuredWindowRateControlTest {

  @MiskTestModule val module = FakeMetricsModule()
  @Inject lateinit var metrics: FakeMetrics

  fun maxEventRate(n : Int) : WebConfig {
    val webConfig = mock(WebConfig::class.java)
    `when`(webConfig.jetty_http2_max_events_per_second).thenReturn(n)
    return webConfig
  }

  @Test
  fun `allows events when under maxEvents limit`() {
    val rateControl = MeasuredWindowRateControl.Factory(
      metrics,
      maxEventRate(5)
    ).newRateControl(null)

    repeat(5) {
      assertThat(rateControl.onEvent("test")).isTrue()
    }
  }

  @Test
  fun `blocks events when over maxEvents limit`() {
    val rateControl = MeasuredWindowRateControl.Factory(
      metrics,
      maxEventRate(2)
    ).newRateControl(null)

    assertThat(rateControl.onEvent("test1")).isTrue()
    assertThat(rateControl.onEvent("test2")).isTrue()
    assertThat(rateControl.onEvent("test3")).isFalse()
    assertThat(rateControl.onEvent("test4")).isFalse()
  }

  @Test
  fun `allows unlimited events when maxEvents is -1`() {
    val rateControl = MeasuredWindowRateControl.Factory(
      metrics,
      maxEventRate(-1)
    ).newRateControl(null)

    repeat(100) {
      assertThat(rateControl.onEvent("test")).isTrue()
    }
  }

  @Test
  fun `window sliding allows events after time passes`() {
    val rateControl = MeasuredWindowRateControl.Factory(
      metrics,
      maxEventRate(2)
    ).newRateControl(null)

    assertThat(rateControl.onEvent("test1")).isTrue()
    assertThat(rateControl.onEvent("test2")).isTrue()
    assertThat(rateControl.onEvent("test3")).isFalse()

    Thread.sleep(1100)

    assertThat(rateControl.onEvent("test4")).isTrue()
    assertThat(rateControl.onEvent("test5")).isTrue()
    assertThat(rateControl.onEvent("test6")).isFalse()
  }
}

