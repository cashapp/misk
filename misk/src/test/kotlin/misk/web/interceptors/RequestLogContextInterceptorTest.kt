package misk.web.interceptors

import com.squareup.moshi.Moshi
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.moshi.adapter
import misk.security.authz.AccessControlModule
import misk.security.authz.FakeCallerAuthenticator
import misk.security.authz.MiskCallerAuthenticator
import misk.security.authz.Unauthenticated
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.WebTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import javax.inject.Inject

@MiskTest(startService = true)
internal class RequestLogContextInterceptorTest {
  data class RequestContext(val fields: Map<String, String>)

  @MiskTestModule
  val module = TestModule()
  val httpClient = OkHttpClient()

  @Inject private lateinit var moshi: Moshi
  @Inject private lateinit var jettyService: JettyService

  @Test fun setLogContext() {
    val response = invoke("caller")
    assertThat(response.code).isEqualTo(200)

    val contextFields =
      moshi.adapter<RequestContext>().fromJson(response.body!!.string())!!.fields
    assertThat(contextFields[RequestLogContextInterceptor.MDC_ACTION])
      .isEqualTo("LogTestAction")
    assertThat(contextFields[RequestLogContextInterceptor.MDC_CALLING_PRINCIPAL])
      .isEqualTo("caller")
    assertThat(contextFields[RequestLogContextInterceptor.MDC_HTTP_METHOD])
      .isEqualTo("GET")
    assertThat(contextFields[RequestLogContextInterceptor.MDC_PROTOCOL])
      .isEqualTo("HTTP/1.1")
    assertThat(contextFields[RequestLogContextInterceptor.MDC_REQUEST_URI])
      .isEqualTo("/call/me")
    assertThat(contextFields[RequestLogContextInterceptor.MDC_REMOTE_ADDR]).isNotEmpty()
  }

  fun invoke(asService: String? = null): okhttp3.Response {
    val url = jettyService.httpServerUrl.newBuilder()
      .encodedPath("/call/me")
      .build()

    val request = okhttp3.Request.Builder()
      .url(url)
      .get()
    asService?.let { request.addHeader(FakeCallerAuthenticator.SERVICE_HEADER, it) }
    return httpClient.newCall(request.build()).execute()
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(AccessControlModule())
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()
      install(WebActionModule.create<LogTestAction>())
    }
  }
}

internal class LogTestAction @Inject constructor() : WebAction {
  @Get("/call/me")
  @Unauthenticated
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun call() = RequestLogContextInterceptorTest.RequestContext(MDC.getCopyOfContextMap())
}
