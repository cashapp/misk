package misk.web.marshal

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
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.MultipartReader
import okhttp3.OkHttpClient
import okhttp3.Request.Builder
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
internal class MultipartRequestTest {
  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var jettyService: JettyService

  @Test
  fun `happy path`() {
    val multipartBody = MultipartBody.Builder()
      .addPart(
        headersOf("a", "apple", "b", "banana"),
        "fruit salad!\n".toRequestBody("text/plain".toMediaType())
      )
      .addFormDataPart("d", "good doggo")
      .build()

    val request = Builder()
      .url(jettyService.httpServerUrl.resolve("/echo-multipart")!!)
      .post(multipartBody)
    val httpClient = OkHttpClient()
    val response = httpClient.newCall(request.build()).execute()

    assertThat(response.code).isEqualTo(200)
    assertThat(response.header("Content-Type")).isEqualTo(MediaTypes.TEXT_PLAIN_UTF8)
    val body = response.body?.string()!!
    assertThat(body).isEqualTo(
      """
        |part 0:
        |  a: apple
        |  b: banana
        |  Content-Type: text/plain; charset=utf-8
        |  Content-Length: 13
        |fruit salad!
        |
        |part 1:
        |  Content-Disposition: form-data; name="d"
        |  Content-Length: 10
        |good doggo
        |""".trimMargin()
    )
  }

  @Test
  fun `missing boundary parameter`() {
    val request = Builder()
      .url(jettyService.httpServerUrl.resolve("/echo-multipart")!!)
      .post("not actually multipart!".toRequestBody("multipart/mixed".toMediaType()))
    val httpClient = OkHttpClient()
    val response = httpClient.newCall(request.build()).execute()

    assertThat(response.code).isEqualTo(400)
    val body = response.body?.string()!!
    assertThat(body).isEqualTo("required boundary parameter missing")
  }

  class EchoMultipart @Inject constructor() : WebAction {
    @Post("/echo-multipart")
    @RequestContentType("multipart/*")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun call(@RequestBody multipartReader: MultipartReader): String {
      val result = Buffer()
      var index = 0
      while (true) {
        val nextPart = multipartReader.nextPart() ?: break
        result.writeUtf8("part $index:\n")
        for ((name, value) in nextPart.headers) {
          result.writeUtf8("  $name: $value\n")
        }
        result.writeAll(nextPart.body)
        result.writeUtf8("\n")
        index++
      }
      return result.readUtf8()
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<EchoMultipart>())
    }
  }
}
