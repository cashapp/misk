package misk.web.extractors

import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.RequestHeader
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import okhttp3.Headers
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
internal class RequestHeaderParameterTest {
  @MiskTestModule
  val module = TestModule()

  @Inject lateinit var jettyService: JettyService

  @Test fun happyPath() {
    assertThat(get("/echo-user-agent", headersOf("cash-user-agent", "Cash App 4.0")))
      .isEqualTo("your user agent is 'Cash App 4.0'")
    assertThat(get("/echo-user-agent", headersOf()))
      .isEqualTo("Required request header Cash-User-Agent not present")
  }

  @Test fun duplicateHeaderShouldError() {
    assertThat(
      get(
        "/echo-user-agent",
        headersOf(
          "cash-user-agent", "Cash App 4.0",
          "cash-user-agent", "Cash App 5.0"
        )
      )
    ).isEqualTo("Multiple values found for [header=Cash-User-Agent], consider using @misk.web.RequestHeaders instead")
  }

  @Test fun optionalParameter() {
    assertThat(get("/echo-optional-user-agent", headersOf("cash-user-agent", "Cash App 4.0")))
      .isEqualTo("your user agent is 'Cash App 4.0'")
    assertThat(get("/echo-optional-user-agent", headersOf()))
      .isEqualTo("your user agent is '<absent>'")
  }

  @Test fun nullableParameter() {
    assertThat(get("/echo-nullable-user-agent", headersOf("cash-user-agent", "Cash App 4.0")))
      .isEqualTo("your user agent is 'Cash App 4.0'")
    assertThat(get("/echo-nullable-user-agent", headersOf()))
      .isEqualTo("your user agent is 'null'")
  }

  @Test fun nullableOptionalParameter() {
    assertThat(get("/echo-nullable-optional-user-agent", headersOf("cash-user-agent", "Cash App 4.0")))
      .isEqualTo("your user agent is 'Cash App 4.0'")
    assertThat(get("/echo-nullable-optional-user-agent", headersOf()))
      .isEqualTo("your user agent is '<absent>'")
  }

  @Test fun typeConvertedParameter() {
    assertThat(get("/echo-user-agent-type", headersOf("cash-user-agent-type", "ANDROID")))
      .isEqualTo("your user agent type is ANDROID")
    assertThat(get("/echo-user-agent-type", headersOf()))
      .isEqualTo("Required request header Cash-User-Agent-Type not present")
  }

  @Test fun multipleRequestHeaders() {
    assertThat(
      get(
        "/multiple-request-headers",
        headersOf(
          "user-agent", "Cash App 4.0",
          "accept-encoding", "gzip"
        )
      )
    ).isEqualTo("your user agent is 'Cash App 4.0' and your accept encoding is 'gzip'")
  }

  class EchoUserAgentAction @Inject constructor() : WebAction {
    @Get("/echo-user-agent")
    fun call(
      @RequestHeader("Cash-User-Agent") userAgent: String
    ) = "your user agent is '$userAgent'"
  }

  class EchoOptionalUserAgentAction @Inject constructor() : WebAction {
    @Get("/echo-optional-user-agent")
    fun call(
      @RequestHeader("Cash-User-Agent") userAgent: String = "<absent>"
    ) = "your user agent is '$userAgent'"
  }

  class EchoNullableUserAgentAction @Inject constructor() : WebAction {
    @Get("/echo-nullable-user-agent")
    fun call(
      @RequestHeader("Cash-User-Agent") userAgent: String?
    ) = "your user agent is '$userAgent'"
  }

  class EchoNullableOptionalUserAgentAction @Inject constructor() : WebAction {
    @Get("/echo-nullable-optional-user-agent")
    fun call(
      @RequestHeader("Cash-User-Agent") userAgent: String? = "<absent>"
    ) = "your user agent is '$userAgent'"
  }

  class EchoUserAgentTypeAction @Inject constructor() : WebAction {
    @Get("/echo-user-agent-type")
    fun call(
      @RequestHeader("Cash-User-Agent-Type") userAgentType: CashUserAgentType
    ) = "your user agent type is $userAgentType"
  }

  class MultipleRequestHeadersAction @Inject constructor() : WebAction {
    @Get("/multiple-request-headers")
    fun call(
      @RequestHeader("User-Agent") userAgent: String,
      @RequestHeader("Accept-Encoding") acceptEncoding: String,
    ) = "your user agent is '$userAgent' and your accept encoding is '$acceptEncoding'"
  }

  enum class CashUserAgentType {
    IOS,
    ANDROID
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<EchoNullableUserAgentAction>())
      install(WebActionModule.create<EchoNullableOptionalUserAgentAction>())
      install(WebActionModule.create<EchoOptionalUserAgentAction>())
      install(WebActionModule.create<EchoUserAgentAction>())
      install(WebActionModule.create<EchoUserAgentTypeAction>())
      install(WebActionModule.create<MultipleRequestHeadersAction>())
    }
  }

  private fun get(path: String, headers: Headers): String {
    val url = jettyService.httpServerUrl.newBuilder()
      .encodedPath(path)
      .build()
    val request = Request(url, headers)
    val httpClient = OkHttpClient()
    val response = httpClient.newCall(request).execute()
    return response.body.source().readUtf8()
  }
}
