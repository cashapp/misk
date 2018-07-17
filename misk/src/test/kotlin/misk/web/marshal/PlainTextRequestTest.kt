package misk.web.marshal

import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.WebActionEntry
import misk.web.WebTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import okhttp3.Request
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

  class PassAsString : WebAction {
    @Post("/as-string")
    @RequestContentType(MediaTypes.TEXT_PLAIN_UTF8)
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun call(@RequestBody message: String): String = "$message as-string"
  }

  class PassAsByteString : WebAction {
    @Post("/as-byte-string")
    @RequestContentType(MediaTypes.TEXT_PLAIN_UTF8)
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun call(@RequestBody messageBytes: ByteString): String = "${messageBytes.utf8()} as-byte-string"
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      multibind<WebActionEntry>().toInstance(WebActionEntry(PassAsString::class))
      multibind<WebActionEntry>().toInstance(WebActionEntry(PassAsByteString::class))
    }
  }

  private fun post(path: String, message: String): String = call(Request.Builder()
      .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())
      .post(okhttp3.RequestBody.create(MediaTypes.TEXT_PLAIN_UTF8_MEDIA_TYPE, message)))

  private fun call(request: Request.Builder): String {
    request.header("Accept", MediaTypes.TEXT_PLAIN_UTF8)

    val httpClient = OkHttpClient()
    val response = httpClient.newCall(request.build()).execute()
    assertThat(response.code()).isEqualTo(200)
    assertThat(response.header("Content-Type")).isEqualTo(MediaTypes.TEXT_PLAIN_UTF8)
    return response.body()?.string()!!
  }
}
