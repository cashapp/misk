package misk.web.resource

import com.google.inject.name.Names
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientModule
import misk.client.HttpClientsConfig
import misk.client.HttpClientsConfigModule
import misk.inject.KAbstractModule
import misk.resources.ResourceLoader
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.WebActionModule
import misk.web.WebTestingModule
import misk.web.jetty.JettyService
import misk.web.resources.StaticResourceAction
import misk.web.resources.StaticResourceEntry
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Named
import kotlin.test.assertFailsWith

@MiskTest(startService = true)
class StaticResourceActionTest {
  @MiskTestModule
  val module = TestModule()

  @Inject @field:Named("static_resource_action") private lateinit var httpClient: OkHttpClient

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
    resourceLoader.put("memory:/web/nasa/command/index.html", """
        |<html>
        |  <body>
        |    <p>Welcome to NASA Space Command Dashboard</p>
        |  </body>
        |</html>
        |""".trimMargin())
    resourceLoader.put("memory:/web/nasa/tabs/o2fuel/tab_o2fuel.js", """
        |alert("nasa world");
        |""".trimMargin())
    resourceLoader.put("memory:/web/nasa/tabs/o2fuel/index.html", """
        |<html>
        |  <body>
        |    <p>Your o2fuel gauge reads: this</p>
        |  </body>
        |</html>
        |""".trimMargin())
    resourceLoader.put("memory:/web/nasa/tabs/o2fuel/tab_o2fuel.css", """
        |nasa > world {
        |  color: blue;
        |}
        |""".trimMargin())
  }

  @Test fun hiRoot() {
    val response = request("/hi/")
    assertThat(response.code).isEqualTo(200)
    assertThat(response.body!!.string()).contains("<p>Hello world</p>")
    assertThat(response.header("Content-Type")).isEqualTo("text/html")
  }

  @Test fun hiHtml() {
    val response = request("/hi/index.html")
    assertThat(response.code).isEqualTo(200)
    assertThat(response.body!!.string()).contains("<p>Hello world</p>")
    assertThat(response.header("Content-Type")).isEqualTo("text/html")
  }

  @Test fun hiCss() {
    val response = request("/hi/main.css")
    assertThat(response.code).isEqualTo(200)
    assertThat(response.body!!.string()).contains("hello > world")
    assertThat(response.header("Content-Type")).isEqualTo("text/css")
  }

  @Test fun hiJs() {
    val response = request("/hi/app.js")
    assertThat(response.code).isEqualTo(200)
    assertThat(response.body!!.string()).contains("alert(\"hello world\")")
    assertThat(response.header("Content-Type")).isEqualTo("application/javascript")
  }

  @Test fun rootUrl() {
    val response = request("/")
    assertThat(response.code).isEqualTo(404)
    assertThat(response.body!!.string()).contains("Nothing found at /")
    assertThat(response.header("Content-Type")).isEqualTo("text/plain;charset=utf-8")
  }

  @Test fun noPathMatch() {
    val response = request("/not/found")
    assertThat(response.code).isEqualTo(404)
    assertThat(response.body!!.string()).contains("Nothing found at /")
    assertThat(response.header("Content-Type")).isEqualTo("text/plain;charset=utf-8")
  }

  @Test fun nasaCommandRoot() {
    val response = request("/nasa/command/")
    assertThat(response.code).isEqualTo(200)
    assertThat(response.body!!.string()).contains(
        "<p>Welcome to NASA Space Command Dashboard</p>")
    assertThat(response.header("Content-Type")).isEqualTo("text/html")
  }

  @Test fun nasaCommandHtml() {
    val response = request("/nasa/command/index.html")
    assertThat(response.code).isEqualTo(200)
    assertThat(response.body!!.string()).contains(
        "<p>Welcome to NASA Space Command Dashboard</p>")
    assertThat(response.header("Content-Type")).isEqualTo("text/html")
  }

  @Test fun nasaFuelRoot() {
    val response = request("/nasa/tabs/o2fuel/")
    assertThat(response.code).isEqualTo(200)
    assertThat(response.body!!.string()).contains(
        "<p>Your o2fuel gauge reads: this</p>")
    assertThat(response.header("Content-Type")).isEqualTo("text/html")
  }

  @Test fun nasaFuelHtml() {
    val response = request("/nasa/tabs/o2fuel/index.html")
    assertThat(response.code).isEqualTo(200)
    assertThat(response.body!!.string()).contains(
        "<p>Your o2fuel gauge reads: this</p>")
    assertThat(response.header("Content-Type")).isEqualTo("text/html")
  }

  @Test fun nasaCss() {
    val response = request("/nasa/tabs/o2fuel/tab_o2fuel.css")
    assertThat(response.code).isEqualTo(200)
    assertThat(response.body!!.string()).contains("nasa > world")
    assertThat(response.header("Content-Type")).isEqualTo("text/css")
  }

  @Test fun nasaJs() {
    val response = request("/nasa/tabs/o2fuel/tab_o2fuel.js")
    assertThat(response.code).isEqualTo(200)
    assertThat(response.body!!.string()).contains("alert(\"nasa world\")")
    assertThat(response.header("Content-Type")).isEqualTo("application/javascript")
  }

  @Test fun nasaCommandBadPathRedirectsToPrefixIndex() {
    val response = request("/nasa/command/another/link/here/for/anti/brevity?with=query")
    assertThat(response.code).isEqualTo(200)
    assertThat(response.body!!.string()).contains(
        "<p>Welcome to NASA Space Command Dashboard</p>")
    assertThat(response.header("Content-Type")).isEqualTo("text/html")
  }

  @Test fun nasaFuelBadPathRedirectsToPrefixIndex() {
    val response = request("/nasa/tabs/o2fuel/another/link/here/for/anti/brevity?with=query")
    assertThat(response.code).isEqualTo(200)
    assertThat(response.body!!.string()).contains(
        "<p>Your o2fuel gauge reads: this</p>")
    assertThat(response.header("Content-Type")).isEqualTo("text/html")
  }

  @Test fun nasaFuelSlashesOnSlashesRedirectsToPrefixIndex() {
    val response = request("/nasa/tabs/o2fuel/////anti/brevity?with=query")
    assertThat(response.code).isEqualTo(200)
    assertThat(response.body!!.string()).contains(
        "<p>Your o2fuel gauge reads: this</p>")
    assertThat(response.header("Content-Type")).isEqualTo("text/html")
  }

  @Test fun nasaFuelSlashesOnSlashesQueryRedirectsToPrefixIndex() {
    val response = request("/nasa/tabs/o2fuel/////anti/brevity?with=query")
    assertThat(response.code).isEqualTo(200)
    assertThat(response.body!!.string()).contains(
        "<p>Your o2fuel gauge reads: this</p>")
    assertThat(response.header("Content-Type")).isEqualTo("text/html")
  }

  @Test fun nasaFuelDotDirectoryRedirectsToPrefixIndex() {
    val response = request("/nasa/tabs/o2fuel/.test/.config/.ssh/anti/brevity?with=query")
    assertThat(response.code).isEqualTo(200)
    assertThat(response.body!!.string()).contains(
        "<p>Your o2fuel gauge reads: this</p>")
    assertThat(response.header("Content-Type")).isEqualTo("text/html")
  }

  @Test fun blockedPrefixEntryFails() {
    assertFailsWith<IllegalArgumentException> {
      StaticResourceEntry("/api/", "memory:/web/api/")
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(HttpClientsConfigModule(HttpClientsConfig(
          endpoints = mapOf(
              "static_resource_action" to HttpClientEndpointConfig("http://example.com/")
          ))))
      install(HttpClientModule("static_resource_action",
          Names.named("static_resource_action")))

      install(WebActionModule.createWithPrefix<StaticResourceAction>("/hi/"))
      multibind<StaticResourceEntry>()
          .toInstance(StaticResourceEntry("/hi/", "memory:/web/hi"))

      install(WebActionModule.createWithPrefix<StaticResourceAction>("/nasa/command/"))
      multibind<StaticResourceEntry>()
          .toInstance(StaticResourceEntry("/nasa/command/", "memory:/web/nasa/command/"))
      install(WebActionModule.createWithPrefix<StaticResourceAction>("/nasa/tabs/o2fuel/"))
      multibind<StaticResourceEntry>()
          .toInstance(StaticResourceEntry("/nasa/tabs/o2fuel/", "memory:/web/nasa/tabs/o2fuel/"))

      install(WebTestingModule())
    }
  }

  private fun request(path: String): okhttp3.Response {
    return httpClient.newCall(okhttp3.Request.Builder()
        .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())
        .build())
        .execute()
  }
}
