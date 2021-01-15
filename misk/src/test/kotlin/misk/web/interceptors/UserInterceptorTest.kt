package misk.web.interceptors

import misk.Action
import misk.ApplicationInterceptor
import misk.Chain
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.PathParam
import misk.web.Response
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
class UserInterceptorTest {
  @MiskTestModule
  val module = TestModule()

  @Inject internal lateinit var jettyService: JettyService

  @Test
  fun stringResponse() {
    val response = get("/call/textResponse")
    assertThat(response.code).isEqualTo(418)
    assertThat(response.header("Content-Type")).isEqualTo(MediaTypes.TEXT_PLAIN_UTF8)
    assertThat(response.body?.string()).isEqualTo("text response")
  }

  @Test
  fun rawStringResponse() {
    val response = get("/call/text")
    assertThat(response.code).isEqualTo(200)
    assertThat(response.header("Content-Type")).isEqualTo(MediaTypes.TEXT_PLAIN_UTF8)
    assertThat(response.body?.string()).isEqualTo("text")
  }

  @Test
  fun throwResponse() {
    val response = get("/call/throw")
    assertThat(response.code).isEqualTo(500)
    assertThat(response.body?.string()).isEqualTo("internal server error")
  }

  @Test
  fun stringNetworkResponse() {
    val response = get("/call/textResponse", "text")
    assertThat(response.code).isEqualTo(410)
    assertThat(response.header("Content-Type")).isEqualTo(MediaTypes.TEXT_PLAIN_UTF8)
    assertThat(response.body?.string()).isEqualTo("net text response")
  }

  @Test
  fun throwNetworkResponse() {
    val response = get("/call/textResponse", "throw")
    assertThat(response.code).isEqualTo(500)
    assertThat(response.body?.string()).isEqualTo("internal server error")
  }

  internal class UserCreatedNetworkInterceptor : NetworkInterceptor {
    override fun intercept(chain: NetworkChain) {
      when (chain.httpCall.requestHeaders.get("mode")) {
        "text" -> {
          chain.httpCall.statusCode = 410
          chain.httpCall.addResponseHeaders(TEXT_HEADERS)
          chain.httpCall.takeResponseBody()!!.use {
            it.writeUtf8("net text response")
          }
        }
        "throw" -> throw Exception("Don't throw exceptions like this")
        else -> chain.proceed(chain.httpCall)
      }
    }

    class Factory : NetworkInterceptor.Factory {
      override fun create(action: Action): NetworkInterceptor? = UserCreatedNetworkInterceptor()
    }
  }

  internal class UserCreatedInterceptor : ApplicationInterceptor {
    override fun intercept(chain: Chain): Any = when (chain.args.firstOrNull()) {
      "text" -> "text"
      "textResponse" -> Response("text response", TEXT_HEADERS, 418)
      "throw" -> throw Exception("Don't throw exceptions like this")
      else -> chain.proceed(chain.args)
    }

    class Factory : ApplicationInterceptor.Factory {
      override fun create(action: Action): ApplicationInterceptor? = UserCreatedInterceptor()
    }
  }

  internal class TestAction @Inject constructor() : WebAction {
    @Get("/call/{responseType}")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun call(@Suppress("UNUSED_PARAMETER") @PathParam responseType: String): TestActionResponse {
      return TestActionResponse("foo")
    }
  }

  internal data class TestActionResponse(val text: String)

  private fun get(path: String, mode: String = "normal"): okhttp3.Response = call(Request.Builder()
      .addHeader("mode", mode)
      .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())
      .get())

  private fun call(request: Request.Builder): okhttp3.Response {
    val httpClient = OkHttpClient()
    val response = httpClient.newCall(request.build()).execute()
    return response
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      multibind<NetworkInterceptor.Factory>().toInstance(UserCreatedNetworkInterceptor.Factory())
      multibind<ApplicationInterceptor.Factory>().toInstance(UserCreatedInterceptor.Factory())

      install(WebActionModule.create<TestAction>())
    }
  }

  companion object {
    internal val TEXT_HEADERS: Headers = Headers.Builder()
        .set("Content-Type", MediaTypes.TEXT_PLAIN_UTF8)
        .build()
  }
}
