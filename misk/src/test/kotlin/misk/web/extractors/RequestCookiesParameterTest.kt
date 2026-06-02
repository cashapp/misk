package misk.web.extractors

import jakarta.inject.Inject
import javax.servlet.http.Cookie
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.RequestCookies
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
internal class RequestCookiesParameterTest {
  @MiskTestModule val module = TestModule()

  @Inject lateinit var jettyService: JettyService

  @Test
  fun multipleRequestCookies() {
    assertThat(get(listOf("app_token" to "AppToken1", "app_platform" to "IOS")))
      .isEqualTo("your cookies are [app_token=AppToken1, app_platform=IOS]'")
  }

  @Test
  fun duplicateRequestCookies() {
    assertThat(get(listOf("app_token" to "AppToken1", "app_token" to "AppToken2", "app_platform" to "IOS")))
      .isEqualTo("your cookies are [app_token=AppToken1, app_token=AppToken2, app_platform=IOS]'")
  }

  @Test
  fun noRequestCookies() {
    assertThat(get(listOf())).isEqualTo("your cookies are []'")
  }

  class EchoCookiesAction @Inject constructor() : WebAction {
    @Get("/echo-cookies")
    fun call(@RequestCookies cookies: List<Cookie>) = "your cookies are ${cookies.map { "${it.name}=${it.value}" }}'"
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<EchoCookiesAction>())
    }
  }

  private fun get(cookies: List<Pair<String, String>>): String {
    val url = jettyService.httpServerUrl.newBuilder().encodedPath("/echo-cookies").build()
    val request =
      Request(url)
        .newBuilder()
        .also { request ->
          cookies.forEach { cookie -> request.addHeader("Cookie", "${cookie.first}=${cookie.second}") }
        }
        .build()
    val httpClient = OkHttpClient()
    val response = httpClient.newCall(request).execute()
    return response.body.source().readUtf8()
  }
}
