package misk.web.interceptors

import io.prometheus.client.Histogram
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
import org.junit.jupiter.api.Test
import jakarta.inject.Inject
import org.assertj.core.data.Offset
import kotlin.time.Duration.Companion.milliseconds

@MiskTest(startService = true)
class ExclusiveTimingInterceptorTest {
  @MiskTestModule
  val module = TestModule()
  val httpClient = OkHttpClient()

  @Inject private lateinit var exclusiveTimingInterceptorFactory: ExclusiveTimingInterceptor.Factory
  @Inject private lateinit var metricsInterceptorFactory: MetricsInterceptor.Factory
  @Inject private lateinit var jettyService: JettyService

  private fun labels(code: Int, service: String = "unknown") =
    arrayOf("ExclusiveTimingInterceptorTestAction", service, code.toString())

  @Test
  fun time() {
    // Fire off a request
    val response = invoke(200)
    assertThat(response.code).isEqualTo(200)

    // Figure out how long each of the latency metrics was
    val requestDuration = metricsInterceptorFactory.requestDuration
    val exclusiveRequestDuration = exclusiveTimingInterceptorFactory.requestDurationHistogram
    val difference =
      requestDuration.labels(*labels(200)).get().sum -
        exclusiveRequestDuration.labels(*labels(200)).get().sum

    // Verify that the sleep time was excluded
    // (but leave some room for small differences due to execution time.)
    assertThat(difference).isCloseTo(
      /* expected = */ ExclusiveTimingInterceptorTestAction.SLEEP_TIME.toDouble(),
      /* offset = */ Offset.offset(ExclusiveTimingInterceptorTestAction.SLEEP_TIME.toDouble() / 2)
    )
  }

  @Test
  fun responseCodes() {
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

    val metric = exclusiveTimingInterceptorFactory.requestDurationHistogram

    // Make sure all the right metrics were generated
    // Note: buckets.last() always contains the count of samples
    assertThat(metric.labels(*labels(200)).get().count()).isEqualTo(2)
    assertThat(metric.labels(*labels(202)).get().count()).isEqualTo(1)
    assertThat(metric.labels(*labels(404)).get().count()).isEqualTo(1)
    assertThat(metric.labels(*labels(403)).get().count()).isEqualTo(2)
    assertThat(metric.labels(*labels(200, "my-peer")).get().count()).isEqualTo(4)
    assertThat(metric.labels(*labels(200, "<user>")).get().count()).isEqualTo(1)
  }

  fun invoke(
    desiredStatusCode: Int,
    service: String? = null,
    user: String? = null,
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
      install(WebActionModule.create<ExclusiveTimingInterceptorTestAction>())

      bind<MetricsInterceptor.Factory>()
      install(ExclusiveTimingInterceptor.Module())
    }
  }
}

internal class ExclusiveTimingInterceptorTestAction
@Inject constructor(private val excludedTime: ThreadLocal<ExcludedTime>) : WebAction {
  @Get("/call/{desiredStatusCode}")
  @Unauthenticated
  fun call(@PathParam desiredStatusCode: Int): Response<String> {
    Thread.sleep(SLEEP_TIME)
    excludedTime.get().add(SLEEP_TIME.milliseconds)
    return Response("foo", statusCode = desiredStatusCode)
  }

  companion object {
    const val SLEEP_TIME: Long = 10
  }
}
