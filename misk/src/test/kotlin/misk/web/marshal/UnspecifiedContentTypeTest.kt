package misk.web.marshal

import com.squareup.moshi.Moshi
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
import misk.web.WebTestClient
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import okio.buffer
import okio.source
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestTemplate
import java.io.ByteArrayInputStream
import javax.inject.Inject

@MiskTest(startService = true)
internal class UnspecifiedContentTypeTest {
  data class Packet(val message: String)

  @MiskTestModule
  val module = TestModule()

  @Inject lateinit var moshi: Moshi
  @Inject lateinit var  jettyService: JettyService


  @Test
  fun `return a 400 if ContentType is not set and can't use generic`() {
    val httpClient = OkHttpClient()

    val request  = Request.Builder()
      .post("".toRequestBody(null))
      .url(jettyService.httpServerUrl.newBuilder().encodedPath("/as-string").build())

    val response = httpClient.newCall(request.build()).execute()

    assertThat(response.code).isEqualTo(400)
    assertThat(response.body?.string()).isEqualTo("Can't parse request: missing Content-Type header")
  }

  @Test
  fun `return a 500 if no marshall for content type`() {
    val httpClient = OkHttpClient()

    val request  = Request.Builder()
      .post("".toRequestBody("application/random".toMediaType()))
      .url(jettyService.httpServerUrl.newBuilder().encodedPath("/as-random-content-type").build())

    val response = httpClient.newCall(request.build()).execute()

    assertThat(response.code).isEqualTo(500)
    assertThat(response.body?.string()).isEqualTo("internal server error")
  }

  class PassAsString @Inject constructor() : WebAction {
    @Post("/as-string")
    @RequestContentType(MediaTypes.TEXT_PLAIN_UTF8)
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun call(@RequestBody message: Packet): String = "$message as-string"
  }

  class RandomContentType @Inject constructor() : WebAction {
    @Post("/as-random-content-type")
    @RequestContentType("application/random")
    @ResponseContentType("application/random")
    fun call(@RequestBody message: Packet): String = "$message as-string"
  }


  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<PassAsString>())
      install(WebActionModule.create<RandomContentType>())
    }
  }
}

