package misk.web.extractors

import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection.HTTP_BAD_REQUEST

/**
 * Tests that JSON parsing exceptions (JsonDataException) are properly mapped to HTTP 400 Bad
 * Request responses via exception mappers.
 *
 * Note: JsonEncodingException extends IOException and is already handled by RequestBodyFeatureBinding.
 */
@MiskTest(startService = true)
internal class JsonRequestBodyExceptionTest {

  @MiskTestModule
  val module = TestModule()

  @Inject
  lateinit var jettyService: JettyService

  private fun serverUrlBuilder(): HttpUrl.Builder {
    return jettyService.httpServerUrl.newBuilder()
  }

  @Test
  fun `invalid JSON structure returns 400 Bad Request`() {
    // Send JSON with wrong type: item_list expects objects but we send strings
    val invalidJson = """{"name": "test", "count": "not-a-number"}"""
    val response = postJson("/echo", invalidJson)
    assertThat(response.code).isEqualTo(HTTP_BAD_REQUEST)
  }

  @Test
  fun `valid JSON returns 200 OK`() {
    val validJson = """{"name": "test", "count": 5}"""
    val response = postJson("/echo", validJson)
    assertThat(response.code).isEqualTo(200)
  }

  private fun postJson(path: String, body: String): okhttp3.Response {
    val httpClient = OkHttpClient()
    val request = Request.Builder()
      .post(body.toRequestBody("application/json".toMediaType()))
      .url(serverUrlBuilder().encodedPath(path).build())
      .build()
    return httpClient.newCall(request).execute()
  }

  data class EchoRequest(val name: String, val count: Int)

  class EchoAction @Inject constructor() : WebAction {
    @Post("/echo")
    @RequestContentType(MediaTypes.APPLICATION_JSON)
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun echo(@RequestBody request: EchoRequest): EchoRequest {
      return request
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<EchoAction>())
    }
  }
}
