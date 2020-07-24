package misk.web.marshal

import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Post
import misk.web.ConcurrencyLimitsOptOut
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
internal class PlainTextRequestTest {
  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var jettyService: JettyService

  @Test
  fun passAsString() {
    assertThat(post("/as-string", "foo")).isEqualTo("foo as-string")
  }

  @Test
  fun passAsByteString() {
    assertThat(post("/as-byte-string", "foo")).isEqualTo("foo as-byte-string")
  }

  class PassAsString @Inject constructor() : WebAction {
    @Post("/as-string")
    @ConcurrencyLimitsOptOut // TODO: Remove after 2020-08-01 (or use @AvailableWhenDegraded).
    @RequestContentType(MediaTypes.TEXT_PLAIN_UTF8)
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun call(@RequestBody message: String): String = "$message as-string"
  }

  class PassAsByteString @Inject constructor() : WebAction {
    @Post("/as-byte-string")
    @ConcurrencyLimitsOptOut // TODO: Remove after 2020-08-01 (or use @AvailableWhenDegraded).
    @RequestContentType(MediaTypes.TEXT_PLAIN_UTF8)
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun call(@RequestBody messageBytes: ByteString): String = "${messageBytes.utf8()} as-byte-string"
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      install(WebActionModule.create<PassAsString>())
      install(WebActionModule.create<PassAsByteString>())
    }
  }

  private fun post(path: String, message: String): String = call(Request.Builder()
      .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())
      .post(message.toRequestBody(MediaTypes.TEXT_PLAIN_UTF8_MEDIA_TYPE)))

  private fun call(request: Request.Builder): String {
    request.header("Accept", MediaTypes.TEXT_PLAIN_UTF8)

    val httpClient = OkHttpClient()
    val response = httpClient.newCall(request.build()).execute()
    assertThat(response.code).isEqualTo(200)
    assertThat(response.header("Content-Type")).isEqualTo(MediaTypes.TEXT_PLAIN_UTF8)
    return response.body?.string()!!
  }
}
