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
import okhttp3.internal.EMPTY_REQUEST
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
internal class GzipTest {
  @MiskTestModule val module = TestModule()

  @Inject lateinit var jetty: JettyService
  @Inject lateinit var moshi: Moshi

  @Test fun `test gzip get requests`() {
    get("/miskhype/16").assertGzipEncoding(gzipped = true)
    get("/miskhype/8").assertGzipEncoding(gzipped = false)
  }

  @Test fun `test gzip post requests`() {
    post("/miskhype/16").assertGzipEncoding(gzipped = true)
    post("/miskhype/8").assertGzipEncoding(gzipped = false)
  }

  class MiskHypeGetAction @Inject constructor() : WebAction {
    @Get("/miskhype/{times}")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun getMiskHype(@PathParam times: Int): List<String> {
      val list = mutableListOf<String>()
      for (i in 0..times) {
        list.add("miskhype")
      }
      return list
    }
  }

  class MiskHypePostAction @Inject constructor() : WebAction {
    @Post("/miskhype/{times}")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun postMiskHype(@PathParam times: Int): List<String> {
      val list = mutableListOf<String>()
      for (i in 0..times) {
        list.add("miskhype")
      }
      return list
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule(webConfig = WebTestingModule.TESTING_WEB_CONFIG.copy(
          gzip = true,
          minGzipSize = 128
      )))
      install(WebActionModule.create<MiskHypeGetAction>())
      install(WebActionModule.create<MiskHypePostAction>())
    }
  }

  private fun get(path: String): Response = call(Request.Builder()
      .url(jetty.httpServerUrl.newBuilder().encodedPath(path).build()))

  private fun post(path: String): Response = call(Request.Builder()
      .url(jetty.httpServerUrl.newBuilder().encodedPath(path).build())
      .post(EMPTY_REQUEST))

  private fun call(request: Request.Builder): Response {
    val httpClient = OkHttpClient()
    val response = httpClient.newCall(request.build()).execute()
    assertThat(response.code).isEqualTo(200)
    return response
  }

  private fun Response.assertGzipEncoding(gzipped: Boolean) {
    use {
      assertThat(networkResponse?.headers?.get("Content-Encoding") == "gzip")
          .isEqualTo(gzipped)
    }
  }
}
