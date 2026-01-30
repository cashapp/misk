package misk.micrometer

import io.micrometer.core.instrument.MeterRegistry
import jakarta.inject.Inject
import java.util.concurrent.TimeUnit
import misk.MiskTestingServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class MicrometerWebActionMetricsTest {
  @MiskTestModule
  val module =
    object : misk.inject.KAbstractModule() {
      override fun configure() {
        install(MiskTestingServiceModule())
        install(WebServerTestingModule())
        install(MicrometerModule())
        install(MicrometerWebActionMetricsModule())
        install(WebActionModule.create<TestAction>())
      }
    }

  @Inject lateinit var jettyService: JettyService
  @Inject lateinit var meterRegistry: MeterRegistry

  val httpClient = OkHttpClient()

  @Test
  fun `records metrics for successful requests`() {
    val request = Request.Builder().url(jettyService.httpServerUrl.newBuilder().encodedPath("/test").build()).build()

    val response = httpClient.newCall(request).execute()
    assertThat(response.code).isEqualTo(200)
    response.close()

    // Check that the timer was recorded
    val timer =
      meterRegistry
        .find("http.server.requests")
        .tag("action", "TestAction")
        .tag("status", "200")
        .tag("outcome", "SUCCESS")
        .timer()

    assertThat(timer).isNotNull
    assertThat(timer!!.count()).isGreaterThanOrEqualTo(1)
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(0.0)
  }

  @Test
  fun `includes caller tag`() {
    val request = Request.Builder().url(jettyService.httpServerUrl.newBuilder().encodedPath("/test").build()).build()

    httpClient.newCall(request).execute().close()

    val timer = meterRegistry.find("http.server.requests").tag("caller", "unknown").timer()

    assertThat(timer).isNotNull
  }

  @Test
  fun `timer publishes histogram`() {
    val request = Request.Builder().url(jettyService.httpServerUrl.newBuilder().encodedPath("/test").build()).build()

    httpClient.newCall(request).execute().close()

    val timer = meterRegistry.find("http.server.requests").tag("action", "TestAction").timer()

    assertThat(timer).isNotNull

    // Verify the timer has histogram data
    val snapshot = timer!!.takeSnapshot()
    assertThat(snapshot.count()).isGreaterThan(0)
  }

  class TestAction @Inject constructor() : WebAction {
    @Get("/test") @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8) fun get(): String = "ok"
  }
}
