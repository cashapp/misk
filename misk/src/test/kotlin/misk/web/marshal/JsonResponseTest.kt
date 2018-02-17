package misk.web.marshal

import com.google.inject.util.Modules
import com.squareup.moshi.Moshi
import misk.MiskModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.TestWebModule
import misk.web.Get
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSink
import okio.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
internal class JsonResponseTest {
  data class Packet(val message: String)

  @MiskTestModule
  val module = Modules.combine(
      MiskModule(),
      WebModule(),
      TestWebModule(),
      TestModule()
  )

  private @Inject lateinit var jettyService: JettyService
  private @Inject lateinit var moshi: Moshi
  private val packetJsonAdapter get() = moshi.adapter(Packet::class.java)

  @Test
  fun returnAsObject() {
    assertThat(get("/response/as-object").message).isEqualTo("as-object")
  }

  @Test
  fun returnAsString() {
    assertThat(get("/response/as-string").message).isEqualTo("as-string")
  }

  @Test
  fun returnAsByteString() {
    assertThat(get("/response/as-byte-string").message).isEqualTo("as-byte-string")
  }

  @Test
  fun returnAsResponseBody() {
    assertThat(get("/response/as-response-body").message).isEqualTo("as-response-body")
  }

  @Test
  fun returnAsObjectResponse() {
    assertThat(get("/response/as-wrapped-object").message).isEqualTo("as-object")
  }

  @Test
  fun returnAsStringResponse() {
    assertThat(get("/response/as-wrapped-string").message).isEqualTo("as-string")
  }

  @Test
  fun returnAsByteStringResponse() {
    assertThat(get("/response/as-wrapped-byte-string").message).isEqualTo("as-byte-string")
  }

  @Test
  fun returnAsResponseBodyResponse() {
    assertThat(get("/response/as-wrapped-response-body").message).isEqualTo("as-response-body")
  }

  class ReturnAsObject : WebAction {
    @Get("/response/as-object")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call() = Packet("as-object")
  }

  class ReturnAsString : WebAction {
    @Get("/response/as-string")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call() = "{\"message\":\"as-string\"}"
  }

  class ReturnAsByteString : WebAction {
    @Get("/response/as-byte-string")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(): ByteString = ByteString.encodeUtf8("{\"message\":\"as-byte-string\"}")
  }

  class ReturnAsResponseBody : WebAction {
    @Get("/response/as-response-body")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(): ResponseBody = object : ResponseBody {
      override fun writeTo(sink: BufferedSink) {
        sink.writeUtf8("{\"message\":\"as-response-body\"}")
      }
    }
  }

  class ReturnAsObjectResponse : WebAction {
    @Get("/response/as-wrapped-object")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call() = Response(Packet("as-object"))
  }

  class ReturnAsStringResponse : WebAction {
    @Get("/response/as-wrapped-string")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call() = Response("{\"message\":\"as-string\"}")
  }

  class ReturnAsByteStringResponse : WebAction {
    @Get("/response/as-wrapped-byte-string")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call() = Response(ByteString.encodeUtf8("""{"message":"as-byte-string"}"""))
  }

  class ReturnAsResponseBodyResponse : WebAction {
    @Get("/response/as-wrapped-response-body")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call() = Response(ByteString.encodeUtf8("""{"message":"as-response-body"}"""))
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
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

  private fun get(path: String): Packet = call(
      Request.Builder()
          .url(jettyService.serverUrl.newBuilder().encodedPath(path).build())
          .get()
  )

  private fun call(request: Request.Builder): Packet {
    request.header("Accept", MediaTypes.APPLICATION_JSON)

    val httpClient = OkHttpClient()
    val response = httpClient.newCall(request.build())
        .execute()
    assertThat(response.code()).isEqualTo(200)
    assertThat(response.header("Content-Type")).isEqualTo(MediaTypes.APPLICATION_JSON)
    return packetJsonAdapter.fromJson(response.body()!!.source())!!
  }
}
