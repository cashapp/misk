package misk.web

import com.google.inject.util.Modules
import misk.MiskModule
import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.TestWebModule
import misk.web.actions.NotFoundAction
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Singleton

@MiskTest(startService = true)
class StaticResourceMapperTest {
  @MiskTestModule
  val module = Modules.combine(
      MiskModule(),
      WebModule(),
      TestWebModule(),
      TestModule())

  @Inject private lateinit var jettyService: JettyService

  @Test fun action() {
    val response = request("/hello")
    Assertions.assertThat(response.code()).isEqualTo(200)
    Assertions.assertThat(response.body()!!.string()).contains("<p>Hello world</p>")
    Assertions.assertThat(response.header("Content-Type")).isEqualTo("text/html")
  }

  @Test fun html() {
    val response = request("/index.html")
    Assertions.assertThat(response.code()).isEqualTo(200)
    Assertions.assertThat(response.body()!!.string()).contains("<p>Hello world</p>")
    Assertions.assertThat(response.header("Content-Type")).isEqualTo("text/html")
  }

  @Test fun css() {
    val response = request("/main.css")
    Assertions.assertThat(response.code()).isEqualTo(200)
    Assertions.assertThat(response.body()!!.string()).contains("hello > world")
    Assertions.assertThat(response.header("Content-Type")).isEqualTo("text/css")
  }

  @Test fun js() {
    val response = request("/app.js")
    Assertions.assertThat(response.code()).isEqualTo(200)
    Assertions.assertThat(response.body()!!.string()).contains("alert(\"hello world\")")
    Assertions.assertThat(response.header("Content-Type")).isEqualTo("application/javascript")
  }

  @Test fun rootUrl() {
    val response = request("/")
    Assertions.assertThat(response.code()).isEqualTo(404)
    Assertions.assertThat(response.body()!!.string()).contains("Nothing found at /")
    Assertions.assertThat(response.header("Content-Type")).isEqualTo("text/plain;charset=utf-8")
  }

  @Test fun notFound() {
    val response = request("/not/found")
    Assertions.assertThat(response.code()).isEqualTo(404)
    Assertions.assertThat(response.body()!!.string()).contains("Nothing found at /")
    Assertions.assertThat(response.header("Content-Type")).isEqualTo("text/plain;charset=utf-8")
  }

  @Singleton
  class Hello : WebAction {
    @Inject lateinit var staticResourceMapper: StaticResourceMapper

    @Get("/hello")
    fun hello(): Response<ResponseBody> {
      return staticResourceMapper.getResponse("/index.html")!!
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebActionModule.create<Hello>())
      install(WebActionModule.create<NotFoundAction>())
      binder().addMultibinderBinding<StaticResourceMapper.Entry>()
          .toInstance(StaticResourceMapper.Entry("/", "???", "src/test/resources/web"))
    }
  }

  private fun request(path: String): okhttp3.Response {
    val httpClient = OkHttpClient()
    return httpClient.newCall(okhttp3.Request.Builder()
        .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())
        .build())
        .execute()
  }
}
