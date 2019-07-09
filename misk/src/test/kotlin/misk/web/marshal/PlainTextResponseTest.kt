package misk.web.marshal

import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.Response
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
internal class PlainTextResponseTest {
  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var jettyService: JettyService

  @Test
  fun returnAsObject() {
    assertThat(get("/response/as-object")).isEqualTo("as-object")
  }

  @Test
  fun returnAsString() {
    assertThat(get("/response/as-string")).isEqualTo("as-string")
  }

  @Test
  fun returnAsByteString() {
    assertThat(get("/response/as-byte-string")).isEqualTo("as-byte-string")
  }

  @Test
  fun returnAsResponseBody() {
    assertThat(get("/response/as-response-body")).isEqualTo("as-response-body")
  }

  @Test
  fun returnAsObjectResponse() {
    assertThat(get("/response/as-wrapped-object")).isEqualTo("as-object")
  }

  @Test
  fun returnAsStringResponse() {
    assertThat(get("/response/as-wrapped-string")).isEqualTo("as-string")
  }

  @Test
  fun returnAsByteStringResponse() {
    assertThat(get("/response/as-wrapped-byte-string")).isEqualTo("as-byte-string")
  }

  @Test
  fun returnAsResponseBodyResponse() {
    assertThat(get("/response/as-wrapped-response-body")).isEqualTo("as-response-body")
  }

  class ReturnAsObject @Inject constructor() : WebAction {
    @Get("/response/as-object")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun call() = MessageWrapper("as-object")
  }

  class ReturnAsString @Inject constructor() : WebAction {
    @Get("/response/as-string")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun call() = "as-string"
  }

  class ReturnAsByteString @Inject constructor() : WebAction {
    @Get("/response/as-byte-string")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun call(): ByteString = "as-byte-string".encodeUtf8()
  }

  class ReturnAsResponseBody @Inject constructor() : WebAction {
    @Get("/response/as-response-body")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun call() = "as-response-body".toResponseBody()
  }

  class ReturnAsObjectResponse @Inject constructor() : WebAction {
    @Get("/response/as-wrapped-object")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun call(): Response<MessageWrapper> = Response(MessageWrapper("as-object"))
  }

  class ReturnAsStringResponse @Inject constructor() : WebAction {
    @Get("/response/as-wrapped-string")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun call() = Response("as-string")
  }

  class ReturnAsByteStringResponse @Inject constructor() : WebAction {
    @Get("/response/as-wrapped-byte-string")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun call() = Response("as-byte-string".encodeUtf8())
  }

  class ReturnAsResponseBodyResponse @Inject constructor() : WebAction {
    @Get("/response/as-wrapped-response-body")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun call() = Response("as-response-body".encodeUtf8())
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      install(WebActionModule.create<ReturnAsObject>())
      install(WebActionModule.create<ReturnAsString>())
      install(WebActionModule.create<ReturnAsByteString>())
      install(WebActionModule.create<ReturnAsResponseBody>())
      install(WebActionModule.create<ReturnAsObjectResponse>())
      install(WebActionModule.create<ReturnAsStringResponse>())
      install(WebActionModule.create<ReturnAsByteStringResponse>())
      install(WebActionModule.create<ReturnAsResponseBodyResponse>())
    }
  }

  fun get(path: String): String = call(Request.Builder()
      .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())
      .get())

  class MessageWrapper(private val message: String) {
    override fun toString() = message
  }

  private fun call(request: Request.Builder): String {
    request.header("Accept", MediaTypes.TEXT_PLAIN_UTF8)

    val httpClient = OkHttpClient()
    val response = httpClient.newCall(request.build()).execute()
    assertThat(response.code).isEqualTo(200)
    assertThat(response.header("Content-Type")).isEqualTo(MediaTypes.TEXT_PLAIN_UTF8)
    return response.body!!.string()
  }
}
