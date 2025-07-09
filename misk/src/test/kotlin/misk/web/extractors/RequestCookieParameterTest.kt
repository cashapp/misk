package misk.web.extractors

import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.RequestCookie
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.servlet.http.Cookie

@MiskTest(startService = true)
internal class RequestCookieParameterTest {
  @MiskTestModule
  val module = TestModule()

  @Inject lateinit var jettyService: JettyService

  @Test fun happyPath() {
    assertThat(get("/echo-app-token", listOf("app_token" to "AppToken1")))
      .isEqualTo("your app token is 'AppToken1'")
    assertThat(get("/echo-app-token", listOf()))
      .isEqualTo("Required request cookie app_token not present")
  }

  @Test fun duplicateCookieShouldError() {
    assertThat(
      get(
        "/echo-app-token",
        listOf(
          "app_token" to "AppToken1",
          "app_token" to "AppToken2"
        )
      )
    ).isEqualTo("Multiple values found for [cookie=app_token], consider using @misk.web.RequestCookies instead")
  }

  @Test fun optionalParameter() {
    assertThat(get("/echo-optional-app-token", listOf("app_token" to "AppToken1")))
      .isEqualTo("your app token is 'AppToken1'")
    assertThat(get("/echo-optional-app-token", listOf()))
      .isEqualTo("your app token is '<absent>'")
  }

  @Test fun nullableParameter() {
    assertThat(get("/echo-nullable-app-token", listOf("app_token" to "AppToken1")))
      .isEqualTo("your app token is 'AppToken1'")
    assertThat(get("/echo-nullable-app-token", listOf()))
      .isEqualTo("your app token is 'null'")
  }

  @Test fun nullableOptionalParameter() {
    assertThat(get("/echo-nullable-optional-app-token", listOf("app_token" to "AppToken1")))
      .isEqualTo("your app token is 'AppToken1'")
    assertThat(get("/echo-nullable-optional-app-token", listOf()))
      .isEqualTo("your app token is '<absent>'")
  }

  @Test fun typeConvertedParameter() {
    assertThat(get("/echo-app-platform-type", listOf("app_platform" to "ANDROID")))
      .isEqualTo("your app platform is ANDROID")
    assertThat(get("/echo-app-platform-type", listOf()))
      .isEqualTo("Required request cookie app_platform not present")
  }

  @Test fun cookieParameterType() {
    assertThat(get("/echo-app-token-as-cookie", listOf("app_token" to "AppToken1")))
      .isEqualTo("your app token is 'AppToken1'")
    assertThat(get("/echo-app-token-as-cookie", listOf()))
      .isEqualTo("Required request cookie app_token not present")
  }

  @Test fun multipleRequestCookies() {
    assertThat(
      get(
        "/multiple-request-cookies",
        listOf(
          "app_token" to "AppToken1",
          "app_platform" to "IOS",
        )
      )
    ).isEqualTo("your app token is 'AppToken1' and your app platform is 'IOS'")
  }

  class EchoAppTokenAction @Inject constructor() : WebAction {
    @Get("/echo-app-token")
    fun call(
      @RequestCookie("app_token") appToken: String
    ) = "your app token is '$appToken'"
  }

  class EchoOptionalAppTokenAction @Inject constructor() : WebAction {
    @Get("/echo-optional-app-token")
    fun call(
      @RequestCookie("app_token") appToken: String = "<absent>"
    ) = "your app token is '$appToken'"
  }

  class EchoNullableAppTokenAction @Inject constructor() : WebAction {
    @Get("/echo-nullable-app-token")
    fun call(
      @RequestCookie("app_token") appToken: String?
    ) = "your app token is '$appToken'"
  }

  class EchoNullableOptionalAppTokenAction @Inject constructor() : WebAction {
    @Get("/echo-nullable-optional-app-token")
    fun call(
      @RequestCookie("app_token") appToken: String? = "<absent>"
    ) = "your app token is '$appToken'"
  }

  class EchoAppTokenTypeAction @Inject constructor() : WebAction {
    @Get("/echo-app-platform-type")
    fun call(
      @RequestCookie("app_platform") appPlatform: AppPlatform
    ) = "your app platform is $appPlatform"
  }

  class EchoAppTokenAsCookieAction @Inject constructor() : WebAction {
    @Get("/echo-app-token-as-cookie")
    fun call(
      @RequestCookie("app_token") appTokenCookie: Cookie
    ) = "your app token is '${appTokenCookie.value}'"
  }

  class MultipleRequestCookiesAction @Inject constructor() : WebAction {
    @Get("/multiple-request-cookies")
    fun call(
      @RequestCookie("app_token") appToken: String,
      @RequestCookie("app_platform") appPlatform: String,
    ) = "your app token is '$appToken' and your app platform is '$appPlatform'"
  }

  enum class AppPlatform {
    IOS,
    ANDROID
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<EchoNullableAppTokenAction>())
      install(WebActionModule.create<EchoNullableOptionalAppTokenAction>())
      install(WebActionModule.create<EchoOptionalAppTokenAction>())
      install(WebActionModule.create<EchoAppTokenAction>())
      install(WebActionModule.create<EchoAppTokenTypeAction>())
      install(WebActionModule.create<EchoAppTokenAsCookieAction>())
      install(WebActionModule.create<MultipleRequestCookiesAction>())
    }
  }

  private fun get(path: String, cookies: List<Pair<String, String>>): String {
    val url = jettyService.httpServerUrl.newBuilder()
      .encodedPath(path)
      .build()
    val request = Request(url).newBuilder()
      .also { request -> cookies.forEach { cookie -> request.addHeader("Cookie", "${cookie.first}=${cookie.second}") } }
      .build()
    val httpClient = OkHttpClient()
    val response = httpClient.newCall(request).execute()
    return response.body.source().readUtf8()
  }
}
