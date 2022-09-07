package misk.web.actions

import com.google.inject.util.Modules
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientFactory
import misk.inject.KAbstractModule
import misk.security.authz.Unauthenticated
import misk.security.csp.Csp
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Test
import javax.inject.Inject
import kotlin.test.assertEquals

@MiskTest(startService = true)
class CspHeadersTest {
  @MiskTestModule
  val module = Modules.combine(TestWebActionModule(), TestModule())
  @Inject private lateinit var httpClientFactory: HttpClientFactory

  @Inject private lateinit var jetty: JettyService

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebActionModule.create<CspWebAction>())
    }
  }

  @Test
  fun `check the headers are being added correctly`() {
    val response = execute()

    val headerValue = response.headers.get("Content-Security-Policy")!!
    assertEquals("rule1; rule2;", headerValue)
  }

  private fun execute(): Response {
    val client = createOkHttpClient()

    val baseUrl = jetty.httpServerUrl
    val requestBuilder = Request.Builder()
      .url(baseUrl.resolve("/csp")!!)

    val call = client.newCall(requestBuilder.build())
    return call.execute()
  }

  private fun createOkHttpClient(): OkHttpClient {
    val config = HttpClientEndpointConfig(jetty.httpServerUrl.toString())
    return httpClientFactory.create(config)
  }




  class CspWebAction @Inject constructor() : WebAction {
    @Csp(["rule1", "rule2"])
    @Get("/csp")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    @Unauthenticated
    fun get() = "hello world".toResponseBody()
  }

}
