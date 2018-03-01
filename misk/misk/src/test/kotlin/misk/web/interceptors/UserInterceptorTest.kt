package misk.web.interceptors

import com.google.inject.util.Modules
import misk.Action
import misk.Chain
import misk.ApplicationInterceptor
import misk.MiskModule
import misk.NetworkChain
import misk.NetworkInterceptor
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.TestWebModule
import misk.web.Get
import misk.web.PathParam
import misk.web.Response
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebModule
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
  val module = Modules.combine(
      MiskModule(),
      WebModule(),
      TestWebModule(),
      TestModule())

  @Inject internal lateinit var jettyService: JettyService

  @Test
  fun stringResponse() {
    val response = get("/call/textResponse")
    assertThat(response.code()).isEqualTo(418)
    assertThat(response.header("Content-Type")).isEqualTo(MediaTypes.TEXT_PLAIN_UTF8)
    assertThat(response.body()?.string()).isEqualTo("text response")
  }

  @Test
  fun rawStringResponse() {
    val response = get("/call/text")
    assertThat(response.code()).isEqualTo(200)
    assertThat(response.header("Content-Type")).isEqualTo(MediaTypes.TEXT_PLAIN_UTF8)
    assertThat(response.body()?.string()).isEqualTo("text")
  }

  @Test
  fun throwResponse() {
    val response = get("/call/throw")
    assertThat(response.code()).isEqualTo(500)
    assertThat(response.body()?.string()).isEqualTo("internal server error")
  }

  @Test
  fun stringNetworkResponse() {
    val response = get("/call/textResponse", "text")
    assertThat(response.code()).isEqualTo(410)
    assertThat(response.header("Content-Type")).isEqualTo(MediaTypes.TEXT_PLAIN_UTF8)
    assertThat(response.body()?.string()).isEqualTo("net text response")
  }

  @Test
  fun throwNetworkResponse() {
    val response = get("/call/textResponse", "throw")
    assertThat(response.code()).isEqualTo(500)
    assertThat(response.body()?.string()).isEqualTo("internal server error")
  }

  internal class UserCreatedNetworkInterceptor : NetworkInterceptor {
    override fun intercept(chain: NetworkChain): Response<*> = when (chain.request.headers.get(
        "mode")) {
      "text" -> Response("net text response", UserInterceptorTest.TEXT_HEADERS, 410)
      "throw" -> throw Exception("Don't throw exceptions like this")
      else -> chain.proceed(chain.request)
    }

    class Factory : NetworkInterceptor.Factory {
      override fun create(action: Action): NetworkInterceptor? = UserCreatedNetworkInterceptor()
    }
  }

  internal class UserCreatedInterceptor: ApplicationInterceptor {
    override fun intercept(chain: Chain): Any = when (chain.args.firstOrNull()) {
      "text" -> "text"
      "textResponse" -> Response("text response", TEXT_HEADERS, 418)
      "throw" -> throw Exception("Don't throw exceptions like this")
      else -> chain.proceed(chain.args)
    }

    class Factory: ApplicationInterceptor.Factory {
      override fun create(action: Action): ApplicationInterceptor? = UserCreatedInterceptor()
    }
  }

  internal class TestAction : WebAction {
    @Get("/call/{responseType}")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun call(@PathParam responseType: String): TestActionResponse {
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

  internal class TestModule : KAbstractModule() {
    override fun configure() {
      newSetBinder<NetworkInterceptor.Factory>().addBinding()
          .toInstance(UserCreatedNetworkInterceptor.Factory())
      newSetBinder<ApplicationInterceptor.Factory>().addBinding()
          .toInstance(UserCreatedInterceptor.Factory())

      install(WebActionModule.create<TestAction>())
    }
  }

  companion object {
    internal val TEXT_HEADERS: Headers = Headers.Builder()
        .set("Content-Type", MediaTypes.TEXT_PLAIN_UTF8)
        .build()
  }
}
