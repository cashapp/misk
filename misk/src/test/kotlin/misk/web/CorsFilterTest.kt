package misk.web

import com.squareup.moshi.Moshi
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class CorsFilterTest {
  @MiskTestModule val module = TestModule()

  @Inject lateinit var jetty: JettyService
  @Inject lateinit var moshi: Moshi

  // All cross-origin requests are allowed.
  @Test fun `Preflight with cors allowed origin`() {
    val response = preflight("/cors-allow/16")

    assertThat(response.headers.get("Access-Control-Allow-Origin"))
        .isEqualTo("https://localhost:8080")

    val response2 = preflight("/cors-allow/16", "https://misk.net")
    assertThat(response2.headers.get("Access-Control-Allow-Origin"))
        .isEqualTo("https://misk.net")
  }

  // Policy that allows localhost, disallow other domains.
  @Test fun `Preflight to endpoint with more restrictive CORs policy`() {
    val response = preflight("/restrictive")

    assertThat(response.headers.get("Access-Control-Allow-Origin"))
        .isEqualTo("https://localhost:8080")

    // No response headers should be returned.
    val responseNoHeaders = preflight("/restricted", "https://stealyourdata.com")
    assertThat(responseNoHeaders.headers.get("Access-Control-Allow-Origin"))
        .isNull()
  }

  // Does not allow cors on any domain.
  @Test fun `Preflight to path without cors`() {
    val response = preflight("/no-cors")

    assertThat(response.headers.get("Access-Control-Allow-Origin"))
        .isNull()
  }

  class CorsAllowGetAction @Inject constructor() : WebAction {
    @Get("/cors-allow/{times}")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun doGet(@PathParam times: Int) = miskHype(times)
  }

  class RestrictiveCORsAction @Inject constructor() : WebAction {
    @Post("/restrictive")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun doPost() = miskHype(10)
  }

  class NoCorsPolicyAction @Inject constructor() : WebAction {
    @Post("/no-cors")
    @RequestContentType(MediaTypes.APPLICATION_JSON)
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun doPost(@RequestBody request: List<String>): Int = request.count { it == "miskhype" }
  }

  fun preflight(path: String, origin: String = "https://localhost:8080"): Response =
      call(Request.Builder()
          .url(jetty.httpServerUrl.newBuilder().encodedPath(path).build())
          .header("Origin", origin)
          .method("OPTIONS", null))

  fun call(request: Request.Builder): Response {
    val httpClient = OkHttpClient()
    val response = httpClient.newCall(request.build()).execute()
    assertThat(response.code).isEqualTo(200)
    return response
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule(webConfig = WebTestingModule.TESTING_WEB_CONFIG.copy(
          cors = mapOf(
              Pair("/cors-allow/*", CorsConfig()),
              Pair("/restrictive", CorsConfig(allowedOrigins = arrayOf("https://localhost:*")
          ))))))
      install(WebActionModule.create<CorsAllowGetAction>())
      install(WebActionModule.create<RestrictiveCORsAction>())
      install(WebActionModule.create<NoCorsPolicyAction>())
    }
  }

  companion object {
    fun miskHype(times: Int): List<String> {
      val list = mutableListOf<String>()
      for (i in 0 until times) {
        list.add("miskhype")
      }
      return list
    }
  }
}
