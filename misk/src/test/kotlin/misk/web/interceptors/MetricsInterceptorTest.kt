package misk.web.interceptors

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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
class MetricsInterceptorTest {
  @MiskTestModule
  val module = TestModule()
  val httpClient = OkHttpClient()

  @Inject private lateinit var metricsInterceptorFactory: MetricsInterceptor.Factory
  @Inject private lateinit var jettyService: JettyService

  @BeforeEach
  fun sendRequests() {
    assertThat(invoke(200).code).isEqualTo(200)
    assertThat(invoke(200).code).isEqualTo(200)
    assertThat(invoke(202).code).isEqualTo(202)
    assertThat(invoke(404).code).isEqualTo(404)
    assertThat(invoke(403).code).isEqualTo(403)
    assertThat(invoke(403).code).isEqualTo(403)
    assertThat(invoke(200, "my-peer").code).isEqualTo(200)
    assertThat(invoke(200, "my-peer").code).isEqualTo(200)
    assertThat(invoke(200, "my-peer").code).isEqualTo(200)
    assertThat(invoke(200, "my-peer").code).isEqualTo(200)
    assertThat(invoke(200, user = "some-user").code).isEqualTo(200)
  }

  @Test
  fun responseCodes() {
    val requestDuration = metricsInterceptorFactory.requestDurationSummary
    requestDuration.labels("MetricsInterceptorTestAction", "unknown", "200").observe(1.0)
    assertThat(requestDuration.labels("MetricsInterceptorTestAction", "unknown", "200").get().count.toInt()).isEqualTo(3)
    requestDuration.labels("MetricsInterceptorTestAction", "unknown", "202").observe(1.0)
    assertThat(requestDuration.labels("MetricsInterceptorTestAction", "unknown", "202").get().count.toInt()).isEqualTo(2)
    requestDuration.labels("MetricsInterceptorTestAction", "unknown", "404").observe(1.0)
    assertThat(requestDuration.labels("MetricsInterceptorTestAction", "unknown", "404").get().count.toInt()).isEqualTo(2)
    requestDuration.labels("MetricsInterceptorTestAction", "unknown", "403").observe(1.0)
    assertThat(requestDuration.labels("MetricsInterceptorTestAction", "unknown", "403").get().count.toInt()).isEqualTo(3)

    requestDuration.labels("MetricsInterceptorTestAction", "my-peer", "200").observe(1.0)
    assertThat(requestDuration.labels("MetricsInterceptorTestAction", "my-peer", "200").get().count.toInt()).isEqualTo(5)

    requestDuration.labels("MetricsInterceptorTestAction", "<user>", "200").observe(1.0)
    assertThat(requestDuration.labels("MetricsInterceptorTestAction", "<user>", "200").get().count.toInt()).isEqualTo(2)
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
    return httpClient.newCall(request.build()).execute()
  }

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
