package misk.web

import com.google.inject.util.Modules
import misk.MiskModule
import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding
import misk.resources.FakeResourceLoader
import misk.resources.FakeResourceLoaderModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.TestWebModule
import misk.web.actions.NotFoundAction
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.resources.StaticResourceMapper
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Singleton

@MiskTest(startService = true)
class StaticResourceMapperTest {
  @MiskTestModule
  val module = Modules.combine(
      FakeResourceLoaderModule(),
      MiskModule(),
      WebModule(),
      TestWebModule(),
      TestModule())

  @Inject private lateinit var jettyService: JettyService
  @Inject private lateinit var resourceLoader: FakeResourceLoader

  @BeforeEach
  internal fun setUp() {
    resourceLoader.put("web/app.js", """
        |alert("hello world");
        |""".trimMargin())
    resourceLoader.put("web/index.html", """
        |<html>
        |  <body>
        |    <p>Hello world</p>
        |  </body>
        |</html>
        |""".trimMargin())
    resourceLoader.put("web/main.css", """
        |hello > world {
        |  color: blue;
        |}
        |""".trimMargin())
  }

  @Test fun action() {
    val response = request("/hello")
    assertThat(response.code()).isEqualTo(200)
    assertThat(response.body()!!.string()).contains("<p>Hello world</p>")
    assertThat(response.header("Content-Type")).isEqualTo("text/html")
  }

  @Test fun html() {
    val response = request("/index.html")
    assertThat(response.code()).isEqualTo(200)
    assertThat(response.body()!!.string()).contains("<p>Hello world</p>")
    assertThat(response.header("Content-Type")).isEqualTo("text/html")
  }

  @Test fun css() {
    val response = request("/main.css")
    assertThat(response.code()).isEqualTo(200)
    assertThat(response.body()!!.string()).contains("hello > world")
    assertThat(response.header("Content-Type")).isEqualTo("text/css")
  }

  @Test fun js() {
    val response = request("/app.js")
    assertThat(response.code()).isEqualTo(200)
    assertThat(response.body()!!.string()).contains("alert(\"hello world\")")
    assertThat(response.header("Content-Type")).isEqualTo("application/javascript")
  }

  @Test fun rootUrl() {
    val response = request("/")
    assertThat(response.code()).isEqualTo(404)
    assertThat(response.body()!!.string()).contains("Nothing found at /")
    assertThat(response.header("Content-Type")).isEqualTo("text/plain;charset=utf-8")
  }

  @Test fun notFound() {
    val response = request("/not/found")
    assertThat(response.code()).isEqualTo(404)
    assertThat(response.body()!!.string()).contains("Nothing found at /")
    assertThat(response.header("Content-Type")).isEqualTo("text/plain;charset=utf-8")
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
          .toInstance(StaticResourceMapper.Entry("/", "web", "???"))
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
