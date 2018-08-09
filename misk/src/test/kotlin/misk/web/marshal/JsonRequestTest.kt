package misk.web.marshal

import com.squareup.moshi.Moshi
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebActionEntry
import misk.web.WebTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString
import okio.Okio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import javax.inject.Inject

@MiskTest(startService = true)
internal class JsonRequestTest {
  data class Packet(val message: String)

  @MiskTestModule
  val module = TestModule()

  @Inject lateinit var moshi: Moshi
  @Inject lateinit var jettyService: JettyService
  private val packetJsonAdapter get() = moshi.adapter(Packet::class.java)

  @Test
  fun passAsObject() {
    assertThat(post("/as-object", Packet("foo")).message).isEqualTo("foo as-object")
  }

  @Test
  fun passAsString() {
    assertThat(post("/as-string", Packet("foo")).message).isEqualTo("foo as-string")
  }

  @Test
  fun passAsByteString() {
    assertThat(post("/as-byte-string", Packet("foo")).message).isEqualTo("foo as-byte-string")
  }

  class PassAsObject : WebAction {
    @Post("/as-object")
    @RequestContentType(MediaTypes.APPLICATION_JSON)
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(@RequestBody packet: Packet) = Packet("${packet.message} as-object")
  }

  class PassAsString : WebAction {
    @Inject lateinit var moshi: Moshi
    private val packetJsonAdapter get() = moshi.adapter(Packet::class.java)

    @Post("/as-string")
    @RequestContentType(MediaTypes.APPLICATION_JSON)
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(@RequestBody encodedPacket: String): Packet {
      val incomingPacket = packetJsonAdapter.fromJson(encodedPacket)
      return Packet("${incomingPacket?.message} as-string")
    }
  }

  class PassAsByteString : WebAction {
    @Inject lateinit var moshi: Moshi
    private val packetJsonAdapter get() = moshi.adapter(Packet::class.java)

    @Post("/as-byte-string")
    @RequestContentType(MediaTypes.APPLICATION_JSON)
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(@RequestBody encodedPacket: ByteString): Packet {
      val source = Okio.buffer(Okio.source(ByteArrayInputStream(encodedPacket.toByteArray())))
      val incomingPacket = packetJsonAdapter.fromJson(source)
      return Packet("${incomingPacket?.message} as-byte-string")
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      multibind<WebActionEntry>().toInstance(WebActionEntry<PassAsObject>())
      multibind<WebActionEntry>().toInstance(WebActionEntry<PassAsString>())
      multibind<WebActionEntry>().toInstance(WebActionEntry<PassAsByteString>())
    }
  }

  private fun post(path: String, packet: Packet): Packet = call(Request.Builder()
      .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())
      .post(okhttp3.RequestBody.create(MediaTypes.APPLICATION_JSON_MEDIA_TYPE,
          packetJsonAdapter.toJson(packet))))

  private fun call(request: Request.Builder): Packet {
    request.header("Accept", MediaTypes.APPLICATION_JSON)

    val httpClient = OkHttpClient()
    val response = httpClient.newCall(request.build()).execute()
    assertThat(response.code()).isEqualTo(200)
    assertThat(response.header("Content-Type")).isEqualTo(MediaTypes.APPLICATION_JSON)
    return packetJsonAdapter.fromJson(response.body()!!.source())!!
  }
}
