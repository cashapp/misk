package misk.web

import misk.inject.KAbstractModule
import misk.resources.ResourceLoader
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.actions.NotFoundAction
import misk.web.actions.WebAction
import misk.web.actions.WebActionEntry
import misk.web.jetty.JettyService
import misk.web.proxy.WebProxyAction
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
  val module = TestModule()

  @Inject private lateinit var jettyService: JettyService
  @Inject private lateinit var resourceLoader: ResourceLoader

  @BeforeEach
  internal fun setUp() {
    resourceLoader.put("memory:/web/hi/app.js", """
        |alert("hello world");
        |""".trimMargin())
    resourceLoader.put("memory:/web/hi/index.html", """
        |<html>
        |  <body>
        |    <p>Hello world</p>
        |  </body>
        |</html>
        |""".trimMargin())
    resourceLoader.put("memory:/web/hi/main.css", """
        |hello > world {
        |  color: blue;
        |}
        |""".trimMargin())
    resourceLoader.put("memory:/web/_admin/lugnut/tab_lugnut.js", """
        |alert("hello world");
        |""".trimMargin())
    resourceLoader.put("memory:/web/_admin/lugnut/index.html", """
        |<html>
        |  <body>
        |    <p>Hello lugnut</p>
        |  </body>
        |</html>
        |""".trimMargin())
    resourceLoader.put("memory:/web/_admin/lugnut/tab_lugnut.css", """
        |hello > world {
        |  color: blue;
        |}
        |""".trimMargin())
  }

  @Test fun basicAction() {
    val response = request("/hello")
    assertThat(response.code()).isEqualTo(200)
    assertThat(response.body()!!.string()).contains("<p>Hello world</p>")
    assertThat(response.header("Content-Type")).isEqualTo("text/html")
  }

  @Test fun basicHtml() {
    val response = request("/hi/index.html")
    assertThat(response.code()).isEqualTo(200)
    assertThat(response.body()!!.string()).contains("<p>Hello world</p>")
    assertThat(response.header("Content-Type")).isEqualTo("text/html")
  }

  @Test fun basicCss() {
    val response = request("/hi/main.css")
    assertThat(response.code()).isEqualTo(200)
    assertThat(response.body()!!.string()).contains("hello > world")
    assertThat(response.header("Content-Type")).isEqualTo("text/css")
  }

  @Test fun basicJs() {
    val response = request("/hi/app.js")
    assertThat(response.code()).isEqualTo(200)
    assertThat(response.body()!!.string()).contains("alert(\"hello world\")")
    assertThat(response.header("Content-Type")).isEqualTo("application/javascript")
  }

  @Test fun basicRootUrl() {
    val response = request("/")
    assertThat(response.code()).isEqualTo(404)
    assertThat(response.body()!!.string()).contains("Nothing found at /")
    assertThat(response.header("Content-Type")).isEqualTo("text/plain;charset=utf-8")
  }

  @Test fun basicNotFound() {
    val response = request("/not/found")
    assertThat(response.code()).isEqualTo(404)
    assertThat(response.body()!!.string()).contains("Nothing found at /")
    assertThat(response.header("Content-Type")).isEqualTo("text/plain;charset=utf-8")
  }

  @Test fun action() {
    val response = request("/_admin/lugnut")
    assertThat(response.code()).isEqualTo(200)
    assertThat(response.body()!!.string()).contains("<p>Hello lugnut</p>")
    assertThat(response.header("Content-Type")).isEqualTo("text/html")
  }

  @Singleton
  class Hello : WebAction {
    @Inject lateinit var staticResourceMapper: StaticResourceMapper

    @Get("/hello/")
    fun hello(): Response<ResponseBody> {
      return staticResourceMapper.getResponse("/hi")!!
    }
  }

  @Singleton
  class ProxyLugnut : WebAction {
    @Inject lateinit var staticResourceMapper: StaticResourceMapper

    @Get("/_admin/{path:.*}")
    @Post("/_admin/{path:.*}")
    fun get(@PathParam path: String): Response<ResponseBody> {
      return staticResourceMapper.getResponse("/_admin/$path")!!
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      multibind<WebActionEntry>().toInstance(WebActionEntry<Hello>())
      multibind<WebActionEntry>().toInstance(WebActionEntry<NotFoundAction>())

      multibind<StaticResourceMapper.Entry>()
          .toInstance(StaticResourceMapper.Entry("/hi/", "memory:/web/hi"))

      multibind<WebActionEntry>().toInstance(WebActionEntry<ProxyLugnut>("/_admin/lugnut"))
      multibind<StaticResourceMapper.Entry>()
          .toInstance(StaticResourceMapper.Entry("/_admin/lugnut/", "memory:/web/_admin/lugnut/"))
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
