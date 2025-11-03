package misk.web.interceptors

import io.prometheus.client.Histogram
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.security.authz.AccessControlModule
import misk.security.authz.FakeCallerAuthenticator
import misk.security.authz.FakeCallerAuthenticator.Companion.SERVICE_HEADER
import misk.security.authz.FakeCallerAuthenticator.Companion.USER_HEADER
import misk.security.authz.MiskCallerAuthenticator
import misk.security.authz.Unauthenticated
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.PathParam
import misk.web.Response
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.awaitility.Durations.ONE_MILLISECOND
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.withPollInterval
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class MetricsInterceptorTest {
  @MiskTestModule
  val module = TestModule()
  val httpClient = OkHttpClient()

  @Inject private lateinit var metricsInterceptorFactory: MetricsInterceptor.Factory
  @Inject private lateinit var jettyService: JettyService

  private fun labels(code: Int, service: String = "unknown") =
    arrayOf("MetricsInterceptorTestAction", service, code.toString())

  @BeforeEach
  fun sendRequests() {
    // Fire off a bunch of requests
    invoke(200)
    invoke(200)
    invoke(202)
    invoke(404)
    invoke(403)
    invoke(403)
    invoke(200, "my-peer")
    invoke(200, "my-peer")
    invoke(200, "my-peer")
    invoke(200, "my-peer")
    invoke(200, user = "some-user")
  }

  @Test
  fun responseCodes() {
    // Make sure all the right non-histo metrics were generated
    val requestDuration = metricsInterceptorFactory.requestDurationSummary!!
    requestDuration.labels(*labels(200)).observe(1.0)
    requestDuration.labels(*labels(202)).observe(1.0)
    requestDuration.labels(*labels(404)).observe(1.0)
    requestDuration.labels(*labels(403)).observe(1.0)
    requestDuration.labels(*labels(200, "my-peer")).observe(1.0)
    requestDuration.labels(*labels(200, "<user>")).observe(1.0)

    // Promteheus processes events asynchronously, thus we might have to wait for a bit.
    await.withPollInterval(ONE_MILLISECOND).atMost(ONE_HUNDRED_MILLISECONDS).untilAsserted {
      // Summary metrics assertions
      assertThat(requestDuration.labels(*labels(200)).get().count.toInt()).isEqualTo(3)
      assertThat(requestDuration.labels(*labels(202)).get().count.toInt()).isEqualTo(2)
      assertThat(requestDuration.labels(*labels(404)).get().count.toInt()).isEqualTo(2)
      assertThat(requestDuration.labels(*labels(403)).get().count.toInt()).isEqualTo(3)
      assertThat(requestDuration.labels(*labels(200, "my-peer")).get().count.toInt()).isEqualTo(5)
      assertThat(requestDuration.labels(*labels(200, "<user>")).get().count.toInt()).isEqualTo(2)

      // Histogram metrics assertions
      val histoDuration = metricsInterceptorFactory.requestDurationHistogram
      assertThat(histoDuration.labels(*labels(200)).get().count()).isEqualTo(2)
      assertThat(histoDuration.labels(*labels(202)).get().count()).isEqualTo(1)
      assertThat(histoDuration.labels(*labels(404)).get().count()).isEqualTo(1)
      assertThat(histoDuration.labels(*labels(403)).get().count()).isEqualTo(2)
      assertThat(histoDuration.labels(*labels(200, "my-peer")).get().count()).isEqualTo(4)
      assertThat(histoDuration.labels(*labels(200, "<user>")).get().count()).isEqualTo(1)
    }
  }

  fun invoke(
    desiredStatusCode: Int,
    service: String? = null,
    user: String? = null
  ): okhttp3.Response {
    val url = jettyService.httpServerUrl.newBuilder()
      .encodedPath("/call/$desiredStatusCode")
      .build()

    val request = okhttp3.Request.Builder()
      .url(url)
      .get()
    service?.let { request.addHeader(SERVICE_HEADER, it) }
    user?.let { request.addHeader(USER_HEADER, it) }
    return httpClient.newCall(request.build()).execute().also {
      // Make sure the request returned the expected code
      assertThat(it.code).isEqualTo(desiredStatusCode)
    }
  }

  private fun Histogram.Child.Value.count() = buckets.last().toInt()

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(AccessControlModule())
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()
      install(WebActionModule.create<MetricsInterceptorTestAction>())

      bind<MetricsInterceptor.Factory>()
    }
  }
}

internal class MetricsInterceptorTestAction @Inject constructor() : WebAction {
  @Get("/call/{desiredStatusCode}")
  @Unauthenticated
  fun call(@PathParam desiredStatusCode: Int): Response<String> {
    return Response("foo", statusCode = desiredStatusCode)
  }
}
