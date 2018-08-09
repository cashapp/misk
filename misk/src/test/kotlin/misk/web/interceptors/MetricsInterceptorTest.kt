package misk.web.interceptors

import misk.inject.KAbstractModule
import misk.metrics.count
import misk.security.authz.AccessControlModule
import misk.security.authz.Unauthenticated
import misk.security.authz.fake.FakeCallerAuthenticator.Companion.SERVICE_HEADER
import misk.security.authz.fake.FakeCallerAuthenticatorModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.PathParam
import misk.web.Response
import misk.web.actions.WebActionEntry
import misk.web.WebTestingModule
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
    assertThat(invoke(200).code()).isEqualTo(200)
    assertThat(invoke(200).code()).isEqualTo(200)
    assertThat(invoke(202).code()).isEqualTo(202)
    assertThat(invoke(404).code()).isEqualTo(404)
    assertThat(invoke(403).code()).isEqualTo(403)
    assertThat(invoke(403).code()).isEqualTo(403)
    assertThat(invoke(200, "my-peer").code()).isEqualTo(200)
    assertThat(invoke(200, "my-peer").code()).isEqualTo(200)
    assertThat(invoke(200, "my-peer").code()).isEqualTo(200)
    assertThat(invoke(200, "my-peer").code()).isEqualTo(200)
  }

  @Test
  fun responseCodes() {
    val requestDuration = metricsInterceptorFactory.requestDuration
    assertThat(requestDuration.labels("TestAction", "unknown", "all").get().count).isEqualTo(6)
    assertThat(requestDuration.labels("TestAction", "unknown", "2xx").get().count).isEqualTo(3)
    assertThat(requestDuration.labels("TestAction", "unknown", "200").get().count).isEqualTo(2)
    assertThat(requestDuration.labels("TestAction", "unknown", "202").get().count).isEqualTo(1)
    assertThat(requestDuration.labels("TestAction", "unknown", "4xx").get().count).isEqualTo(3)
    assertThat(requestDuration.labels("TestAction", "unknown", "404").get().count).isEqualTo(1)
    assertThat(requestDuration.labels("TestAction", "unknown", "403").get().count).isEqualTo(2)

    assertThat(requestDuration.labels("TestAction", "my-peer", "all").get().count).isEqualTo(4)
    assertThat(requestDuration.labels("TestAction", "my-peer", "2xx").get().count).isEqualTo(4)
    assertThat(requestDuration.labels("TestAction", "my-peer", "200").get().count).isEqualTo(4)
  }

  fun invoke(desiredStatusCode: Int, service: String? = null): okhttp3.Response {
    val url = jettyService.httpServerUrl.newBuilder()
        .encodedPath("/call/$desiredStatusCode")
        .build()

    val request = okhttp3.Request.Builder()
        .url(url)
        .get()
    service?.let { request.addHeader(SERVICE_HEADER, it) }
    return httpClient.newCall(request.build()).execute()
  }

  internal class TestAction : WebAction {
    @Get("/call/{desiredStatusCode}")
    @Unauthenticated
    fun call(@PathParam desiredStatusCode: Int): Response<String> {
      return Response("foo", statusCode = desiredStatusCode)
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(AccessControlModule())
      install(WebTestingModule())
      install(FakeCallerAuthenticatorModule())
      multibind<WebActionEntry>().toInstance(WebActionEntry<TestAction>())
    }
  }

}
